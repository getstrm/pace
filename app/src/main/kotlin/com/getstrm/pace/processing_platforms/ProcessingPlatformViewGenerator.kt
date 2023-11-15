package com.getstrm.pace.processing_platforms

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.*
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.FilterCase.GENERIC_FILTER
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.Filter.FilterCase.RETENTION_FILTER
import com.getstrm.pace.util.defaultJooqSettings
import com.getstrm.pace.util.fullName
import com.getstrm.pace.util.headTailFold
import org.jooq.*
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.DSL.*
import org.jooq.Field

abstract class ProcessingPlatformViewGenerator(
    protected val dataPolicy: DataPolicy,
    private val transformer: ProcessingPlatformTransformer = DefaultProcessingPlatformTransformer,
    customJooqSettings: Settings.() -> Unit = {},
) : ProcessingPlatformRenderer {
    protected abstract fun List<DataPolicy.Principal>.toPrincipalCondition(): Condition?

    protected open fun selectWithAdditionalHeaderStatements(fields: List<Field<*>>): SelectSelectStep<Record> =
        jooq.select(fields)

    protected open fun additionalFooterStatements(): Queries = DSL.queries()
;
    protected open fun DataPolicy.RuleSet.Filter.RetentionFilter.Condition.toRetentionCondition(field: DataPolicy.Field): Field<Boolean> =
        if (this.hasPeriod()) {
            DSL.field("{0} + INTERVAL {1} < current_timestamp", Boolean::class.java, DSL.unquotedName(field.fullName()), inline("${this.period.days} days"))
        } else {
            field("true", Boolean::class.java)
        }

    protected open val jooq: DSLContext = DSL.using(SQLDialect.DEFAULT, defaultJooqSettings.apply(customJooqSettings))

    fun toDynamicViewSQL(): String {
        val queries = dataPolicy.ruleSetsList.map { ruleSet ->
            val targetView = ruleSet.target.fullname

            jooq.createOrReplaceView(renderName(targetView)).`as`(
                selectWithAdditionalHeaderStatements(
                    dataPolicy.source.fieldsList.map { field ->
                        toJooqField(
                            field,
                            ruleSet.fieldTransformsList.firstOrNull {
                                it.field.fullName() == field.fullName()
                            },
                        )
                    },
                )
                    .from(renderName(dataPolicy.source.ref)).let {
                        addDetokenizeJoins(it, ruleSet)
                    }
                    .where(
                        ruleSet.filtersList.map { filter ->
                            when (filter.filterCase) {
                                RETENTION_FILTER -> toCondition(filter.retentionFilter)
                                GENERIC_FILTER -> toCondition(filter.genericFilter)
                                else -> throw IllegalArgumentException("Unsupported filter: ${filter.filterCase.name}")
                            }
                        }
                    ),
            )
        }

        val allQueries = queries + additionalFooterStatements()

        return jooq.queries(allQueries).sql
    }

    private fun addDetokenizeJoins(
        selectJoinStep: SelectJoinStep<Record>,
        ruleSet: DataPolicy.RuleSet
    ): SelectJoinStep<Record> {
        var result = selectJoinStep
        ruleSet.fieldTransformsList.forEach { fieldTransform ->
            fieldTransform.transformsList.forEach { transform ->
                if (transform.hasDetokenize()) {
                    result = result.leftOuterJoin(renderName(transform.detokenize.tokenSourceRef))
                        .on(
                            condition(
                                "{0} = {1}",
                                unquotedName("${renderName(dataPolicy.source.ref)}.${fieldTransform.field.fullName()}"),
                                unquotedName("${renderName(transform.detokenize.tokenSourceRef)}.${transform.detokenize.tokenField.fullName()}")
                            )
                        )
                }
            }
        }
        return result
    }

    fun toCondition(filter: DataPolicy.RuleSet.Filter.GenericFilter): Condition {
        if (filter.conditionsList.size == 1) {
            // If there is only one filter it should be the only option
            return getParser().parseCondition(filter.conditionsList.first().condition)
        }

        val whereCondition = filter.conditionsList.headTailFold(
            headOperation = { condition ->
                getParser().parseCondition(condition.condition)
                DSL.`when`(
                    condition.principalsList.toPrincipalCondition(),
                    field(condition.condition, Boolean::class.java),
                )
            },
            bodyOperation = { conditionStep, condition ->
                getParser().parseCondition(condition.condition)
                conditionStep.`when`(
                    condition.principalsList.toPrincipalCondition(),
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

    fun toCondition(retention: DataPolicy.RuleSet.Filter.RetentionFilter): Condition {
        if (retention.conditionsList.size == 1) {
            // If there is only one filter it should be the only option
            // create retention sql
        }

        val whereCondition = retention.conditionsList.headTailFold(
            headOperation = { condition ->
                DSL.`when`(
                    condition.principalsList.toPrincipalCondition(),
                    condition.toRetentionCondition(retention.field),
                )
            },
            bodyOperation = { conditionStep, condition ->
                conditionStep.`when`(
                    condition.principalsList.toPrincipalCondition(),
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
    ): Field<*> {
        if (fieldTransform == null) {
            // If there is no transform, we return just the field path (joined by a dot for now)
            return field(field.fullName())
        }
        if (fieldTransform.transformsList.size == 1) {
            // If there is only one transform it should be the only option
            val (_, queryPart) = toCase(fieldTransform.transformsList.last(), field)
            return queryPart.`as`(field.fullName())
        }

        val caseWhenStatement = fieldTransform.transformsList.headTailFold(
            headOperation = { transform ->
                val (c, q) = toCase(transform, field)
                DSL.`when`(c, q)
            },
            bodyOperation = { conditionStep, transform ->
                val (c, q) = toCase(transform, field)
                conditionStep.`when`(c, q)
            },
            tailOperation = { conditionStep, transform ->
                val (c, q) = toCase(transform, field)
                conditionStep.otherwise(q).`as`(field.fullName())
            },
        )

        return caseWhenStatement
    }

    fun toCase(
        transform: DataPolicy.RuleSet.FieldTransform.Transform?,
        field: DataPolicy.Field,
    ): Pair<Condition?, Field<Any>> {
        val memberCheck = transform?.principalsList?.toPrincipalCondition()

        val statement = when (transform?.transformCase) {
            REGEXP -> transformer.regexpReplace(field, transform.regexp)
            FIXED -> transformer.fixed(field, transform.fixed)
            HASH -> transformer.hash(field, transform.hash)
            SQL_STATEMENT -> transformer.sqlStatement(getParser(), transform.sqlStatement)
            NULLIFY -> transformer.nullify()
            DETOKENIZE -> transformer.detokenize(
                field,
                transform.detokenize,
                dataPolicy.source.ref,
            )

            TRANSFORM_NOT_SET, IDENTITY, null -> transformer.identity(field)
        }
        return memberCheck to (statement as Field<Any>)
    }

    private fun getParser() = jooq.parser()
}
