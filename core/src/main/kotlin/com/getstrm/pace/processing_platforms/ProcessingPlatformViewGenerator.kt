package com.getstrm.pace.processing_platforms

import org.jooq.Field as JooqField
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.AGGREGATION
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.DETOKENIZE
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.FIXED
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.HASH
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.IDENTITY
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.NULLIFY
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.NUMERIC_ROUNDING
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.REGEXP
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.SQL_STATEMENT
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.TRANSFORM_NOT_SET
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.FilterCase.GENERIC_FILTER
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.FilterCase.RETENTION_FILTER
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.Target.TargetType
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceNode
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceUrn
import com.getstrm.pace.util.defaultJooqSettings
import com.getstrm.pace.util.fullName
import com.getstrm.pace.util.headTailFold
import java.sql.Timestamp
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.DatePart
import org.jooq.Queries
import org.jooq.Record
import org.jooq.SQLDialect
import org.jooq.SelectConditionStep
import org.jooq.SelectJoinStep
import org.jooq.SelectSelectStep
import org.jooq.conf.ParamType
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.DSL.condition
import org.jooq.impl.DSL.currentTimestamp
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.timestampAdd
import org.jooq.impl.DSL.trueCondition
import org.jooq.impl.DSL.unquotedName
import org.jooq.impl.DSL.using

abstract class ProcessingPlatformViewGenerator(
    protected val dataPolicy: DataPolicy,
    private val transformer: ProcessingPlatformTransformer = DefaultProcessingPlatformTransformer,
    customJooqSettings: Settings.() -> Unit = {},
) : ProcessingPlatformRenderer {
    abstract fun toPrincipalCondition(
        principals: List<DataPolicy.Principal>,
        target: DataPolicy.Target? = null
    ): Condition?

    protected open fun selectWithAdditionalHeaderStatements(
        fields: List<JooqField<*>>
    ): SelectSelectStep<Record> = jooq.select(fields)

    protected open fun additionalFooterStatements(): Queries = DSL.queries()

    protected open fun DataPolicy.RuleSet.Filter.RetentionFilter.Condition.toRetentionCondition(
        field: DataPolicy.Field
    ): JooqField<Boolean> =
        if (this.hasPeriod()) {
            field(
                "{0} > {1}",
                Boolean::class.java,
                timestampAdd(
                    field(renderName(field.fullName()), Timestamp::class.java),
                    this.period.days,
                    DatePart.DAY
                ),
                currentTimestamp()
            )
        } else {
            field(trueCondition())
        }

    protected open val jooq: DSLContext =
        using(SQLDialect.DEFAULT, defaultJooqSettings.apply(customJooqSettings))

    open fun createOrReplaceView(name: String) = jooq.createOrReplaceView(name)

    open fun toDynamicViewSQL(): Queries {
        val queries =
            dataPolicy.ruleSetsList.map { ruleSet ->
                val targetView = ruleSet.target.ref.integrationFqn
                createOrReplaceView(renderName(targetView)).`as`(toSelectStatement(ruleSet))
            }

        val allQueries = queries + additionalFooterStatements()

        return jooq.queries(allQueries)
    }

    fun toSelectStatement(inlineParameters: Boolean = false): Map<DataPolicy.Target, String> {
        return dataPolicy.ruleSetsList.associate { ruleSet ->
            val selectStatement = toSelectStatement(ruleSet)
            ruleSet.target to if (inlineParameters) selectStatement.getSQL(ParamType.INLINED) else selectStatement.sql
        }
    }

    fun toSelectStatement(ruleSet: DataPolicy.RuleSet): SelectConditionStep<Record> {
        val fromTable =
            when (ruleSet.target.type) {
                TargetType.DBT_SQL ->
                    jooq.renderNamedParams(
                        unquotedName(
                            "{{{ ref('${dataPolicy.source.ref.resourcePathList.last().name}') }}}"
                        )
                    )
                else -> renderName(dataPolicy.source.ref.integrationFqn) // SQL_VIEW is the default
            }
        return selectWithAdditionalHeaderStatements(
                dataPolicy.source.fieldsList.map { field ->
                    toJooqField(
                        field,
                        ruleSet.fieldTransformsList.firstOrNull {
                            it.field.fullName() == field.fullName()
                        },
                        ruleSet.target
                    )
                },
            )
            .from(fromTable)
            .let { addDetokenizeJoins(it, ruleSet) }
            .where(createWhereStatement(ruleSet))
    }

    private fun createWhereStatement(ruleSet: DataPolicy.RuleSet) =
        ruleSet.filtersList.map { filter ->
            when (filter.filterCase) {
                RETENTION_FILTER -> toCondition(filter.retentionFilter, ruleSet.target)
                GENERIC_FILTER -> toCondition(filter.genericFilter, ruleSet.target)
                else ->
                    throw IllegalArgumentException("Unsupported filter: ${filter.filterCase.name}")
            }
        }

    private fun addDetokenizeJoins(
        selectJoinStep: SelectJoinStep<Record>,
        ruleSet: DataPolicy.RuleSet
    ): SelectJoinStep<Record> {
        var result = selectJoinStep
        ruleSet.fieldTransformsList.forEach { fieldTransform ->
            fieldTransform.transformsList.forEach { transform ->
                if (transform.hasDetokenize()) {
                    result =
                        result
                            .leftOuterJoin(renderName(transform.detokenize.tokenSourceRef))
                            .on(
                                condition(
                                    "{0} = {1}",
                                    unquotedName(
                                        "${renderName(dataPolicy.source.ref.integrationFqn)}.${fieldTransform.field.fullName()}"
                                    ),
                                    unquotedName(
                                        "${renderName(transform.detokenize.tokenSourceRef)}.${transform.detokenize.tokenField.fullName()}"
                                    )
                                )
                            )
                }
            }
        }
        return result
    }

    open fun toCondition(
        filter: DataPolicy.RuleSet.Filter.GenericFilter,
        target: DataPolicy.Target
    ): Condition {
        if (filter.conditionsList.size == 1) {
            // If there is only one filter it should be the only option
            return getParser().parseCondition(filter.conditionsList.first().condition)
        }

        val whereCondition =
            filter.conditionsList.headTailFold(
                headOperation = { condition ->
                    getParser().parseCondition(condition.condition)
                    DSL.`when`(
                        toPrincipalCondition(condition.principalsList, target),
                        field(condition.condition, Boolean::class.java),
                    )
                },
                bodyOperation = { conditionStep, condition ->
                    getParser().parseCondition(condition.condition)
                    conditionStep.`when`(
                        toPrincipalCondition(condition.principalsList, target),
                        field(condition.condition, Boolean::class.java),
                    )
                },
                tailOperation = { conditionStep, condition ->
                    getParser().parseCondition(condition.condition)
                    conditionStep.otherwise(field(condition.condition, Boolean::class.java))
                },
            )
        return DSL.condition(whereCondition)
    }

    open fun toCondition(
        retention: DataPolicy.RuleSet.Filter.RetentionFilter,
        target: DataPolicy.Target
    ): Condition {
        if (retention.conditionsList.size == 1) {
            // If there is only one filter it should be the only option
            // create retention sql
            return condition(retention.conditionsList.first().toRetentionCondition(retention.field))
        }

        val whereCondition =
            retention.conditionsList.headTailFold(
                headOperation = { condition ->
                    DSL.`when`(
                        toPrincipalCondition(condition.principalsList, target),
                        condition.toRetentionCondition(retention.field),
                    )
                },
                bodyOperation = { conditionStep, condition ->
                    conditionStep.`when`(
                        toPrincipalCondition(condition.principalsList, target),
                        condition.toRetentionCondition(retention.field),
                    )
                },
                tailOperation = { conditionStep, condition ->
                    conditionStep.otherwise(condition.toRetentionCondition(retention.field))
                },
            )
        return DSL.condition(whereCondition)
    }

    fun toJooqField(
        field: DataPolicy.Field,
        fieldTransform: DataPolicy.RuleSet.FieldTransform?,
        target: DataPolicy.Target
    ): JooqField<*> {
        if (fieldTransform == null) {
            // If there is no transform, we return just the field path (joined by a dot for now)
            return field(renderName(field.fullName()))
        }
        if (fieldTransform.transformsList.size == 1) {
            // If there is only one transform it should be the only option
            val (_, queryPart) = toCase(fieldTransform.transformsList.last(), field, target)
            return queryPart.`as`(renderName(field.fullName()))
        }

        val caseWhenStatement =
            fieldTransform.transformsList.headTailFold(
                headOperation = { transform ->
                    val (c, q) = toCase(transform, field, target)
                    DSL.`when`(c, q)
                },
                bodyOperation = { conditionStep, transform ->
                    val (c, q) = toCase(transform, field, target)
                    conditionStep.`when`(c, q)
                },
                tailOperation = { conditionStep, transform ->
                    val (c, q) = toCase(transform, field, target)
                    conditionStep.otherwise(q).`as`(renderName(field.fullName()))
                },
            )

        return caseWhenStatement
    }

    private fun toCase(
        transform: DataPolicy.RuleSet.FieldTransform.Transform?,
        field: DataPolicy.Field,
        target: DataPolicy.Target
    ): Pair<Condition?, JooqField<Any>> {
        val memberCheck = toPrincipalCondition(transform?.principalsList.orEmpty(), target)
        val implicitType = target.type == TargetType.DBT_SQL

        val statement =
            when (transform?.transformCase) {
                REGEXP -> transformer.regexpReplace(field, transform.regexp)
                FIXED -> transformer.fixed(field, transform.fixed, implicitType)
                HASH -> transformer.hash(field, transform.hash)
                SQL_STATEMENT -> transformer.sqlStatement(getParser(), transform.sqlStatement)
                NULLIFY -> transformer.nullify()
                DETOKENIZE ->
                    transformer.detokenize(
                        field,
                        transform.detokenize,
                        dataPolicy.source.ref.integrationFqn,
                    )
                NUMERIC_ROUNDING -> transformer.numericRounding(field, transform.numericRounding)
                AGGREGATION -> transformer.aggregation(field, transform.aggregation)
                TRANSFORM_NOT_SET,
                IDENTITY,
                null -> transformer.identity(field)
            }
        return memberCheck to (statement as JooqField<Any>)
    }

    private fun getParser() = jooq.parser()
}

// TODO move to its own package. GlobalTransforms
/**
 * enforce non-overlapping principals on the ApiTransforms in one FieldTransform. First one wins.
 *
 * Since each tag can have a list of associated ApiTransforms, this makes the ORDER of tags
 * important. Let's hope the catalogs present the tags in a deterministic order!
 *
 * a certain fields the tags define non-overlapping rules? The [DataPolicyValidatorService.validate]
 * method already executes this check.
 *
 * The strategy here is an ongoing discussion: https://github.com/getstrm/pace/issues/33
 */
fun List<DataPolicy.RuleSet.FieldTransform.Transform>.combineTransforms():
    List<DataPolicy.RuleSet.FieldTransform.Transform> {
    val filtered: List<DataPolicy.RuleSet.FieldTransform.Transform> =
        this.fold(
                emptySet<String>() to listOf<DataPolicy.RuleSet.FieldTransform.Transform>(),
            ) {
                (
                    /* the principals that we've already encountered while going through the list */
                    alreadySeenPrincipals: Set<String>,
                    /* the cleaned-up list of ApiTransforms */
                    acc: List<DataPolicy.RuleSet.FieldTransform.Transform>,
                ),
                /* the original ApiTransform */
                transform: DataPolicy.RuleSet.FieldTransform.Transform,
                ->
                // TODO principals should also support other types than just groups
                val principals =
                    transform.principalsList.map { it.group }.toSet() - alreadySeenPrincipals
                val dataPolicyWithoutOverlappingPrincipals =
                    transform
                        .toBuilder()
                        .clearPrincipals()
                        .addAllPrincipals(
                            principals.map {
                                DataPolicy.Principal.newBuilder().setGroup(it).build()
                            }
                        )
                        .build()
                alreadySeenPrincipals + principals to acc + dataPolicyWithoutOverlappingPrincipals
            }
            .second
    // now remove duplicate defaults (without principals
    val (defaults, withPrincipals) = filtered.partition { it.principalsCount == 0 }
    return (withPrincipals + defaults.firstOrNull()).filterNotNull()
}

/**
 * add a rule set to a data policy based on tags.
 *
 * @param dataPolicy blueprint DataPolicy
 * @return policy with embedded ruleset.
 */
fun addRuleSet(
    dataPolicy: DataPolicy,
    targetType: TargetType = TargetType.SQL_VIEW,
    globalTransformProvider: (tag: String) -> GlobalTransform?
): DataPolicy {
    val fieldTransforms =
        dataPolicy.source.fieldsList
            .filter { it.tagsList.isNotEmpty() }
            .mapNotNull { field ->
                val transforms =
                    field.tagsList
                        .flatMap { tag ->
                            // TODO this should be a batch call for multiple refAndTypes
                            globalTransformProvider(tag)?.tagTransform?.transformsList
                                ?: emptyList()
                        }
                        .combineTransforms()

                return@mapNotNull if (transforms.isNotEmpty()) {
                    DataPolicy.RuleSet.FieldTransform.newBuilder()
                        .setField(field)
                        .addAllTransforms(transforms)
                        .build()
                } else {
                    // Ensure no field transforms are added to the ruleset that don't have any
                    // transforms
                    null
                }
            }

    return if (fieldTransforms.isNotEmpty()) {
        dataPolicy
            .toBuilder()
            .addRuleSets(
                DataPolicy.RuleSet.newBuilder()
                    .setTarget(
                        DataPolicy.Target.newBuilder()
                            .setType(targetType)
                            .setRef(
                                resourceUrn {
                                    integrationFqn = "${dataPolicy.source.ref.integrationFqn}_view"
                                    resourcePath += dataPolicy.source.ref.resourcePathList.mapIndexed { index, resourceNode ->
                                        resourceNode {
                                            name = if (index == dataPolicy.source.ref.resourcePathCount - 1) {
                                                "${resourceNode.name}_view"
                                            } else {
                                                resourceNode.name
                                            }
                                        }
                                    }
                                }
                            )
                            .build()
                    )
                    .addAllFieldTransforms(fieldTransforms)
            )
            .build()
    } else {
        dataPolicy
    }
}
