package com.getstrm.pace.processing_platforms

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.*
import com.getstrm.pace.util.fullName
import com.getstrm.pace.util.headTailFold
import org.jooq.*
import org.jooq.conf.ParseNameCase
import org.jooq.conf.ParseUnknownFunctions
import org.jooq.conf.RenderQuotedNames
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.DSL.*
import org.jooq.Field as JooqField

abstract class AbstractDynamicViewGenerator(
    protected val dataPolicy: DataPolicy,
    customJooqSettings: Settings.() -> Unit = {},
) {
    protected val transformFactory: ProcessingPlatformTransformFactory = ProcessingPlatformTransformFactory()

    protected abstract fun List<DataPolicy.Principal>.toPrincipalCondition(): Condition?

    protected open fun selectWithAdditionalHeaderStatements(fields: List<JooqField<*>>): SelectSelectStep<Record> =
        jooq.select(fields)

    protected open fun additionalFooterStatements(): Queries = DSL.queries()

    protected open fun renderName(name: String): String = jooq.renderNamedParams(name(name))

    protected val jooq = DSL.using(SQLDialect.DEFAULT, defaultJooqSettings.apply(customJooqSettings))
    private val parser = jooq.parser()

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
                            toCondition(filter)
                        },
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

    fun toCondition(filter: DataPolicy.RuleSet.Filter): Condition {
        if (filter.conditionsList.size == 1) {
            // If there is only one filter it should be the only option
            return parser.parseCondition(filter.conditionsList.first().condition)
        }

        val whereCondition = filter.conditionsList.headTailFold(
            headOperation = { condition ->
                parser.parseCondition(condition.condition)
                DSL.`when`(
                    condition.principalsList.toPrincipalCondition(),
                    field(condition.condition, Boolean::class.java),
                )
            },
            bodyOperation = { conditionStep, condition ->
                parser.parseCondition(condition.condition)
                conditionStep.`when`(
                    condition.principalsList.toPrincipalCondition(),
                    field(condition.condition, Boolean::class.java),
                )
            },
            tailOperation = { conditionStep, condition ->
                parser.parseCondition(condition.condition)
                conditionStep.otherwise(field(condition.condition, Boolean::class.java))
            },
        )
        return DSL.condition(whereCondition)
    }

    fun toJooqField(
        field: DataPolicy.Field,
        fieldTransform: DataPolicy.RuleSet.FieldTransform?,
    ): JooqField<*> {
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
    ): Pair<Condition?, JooqField<Any>> {
        val memberCheck = transform?.principalsList?.toPrincipalCondition()

        val statement = when (transform?.transformCase) {
            REGEXP -> transformFactory.regexpReplaceTransform(field, transform.regexp)
            FIXED -> transformFactory.fixedTransform(field, transform.fixed)
            HASH -> transformFactory.hashTransform(field, transform.hash)
            SQL_STATEMENT -> transformFactory.sqlStatementTransform(parser, transform.sqlStatement)
            NULLIFY -> transformFactory.nullifyTransform()
            DETOKENIZE -> transformFactory.detokenizeTransform(
                renderName(transform.detokenize.tokenSourceRef),
                renderName(dataPolicy.source.ref),
                transform.detokenize,
                field
            )
            NUMERIC_ROUNDING -> transformFactory.numericRoundingTransform()
            TRANSFORM_NOT_SET, IDENTITY, null -> transformFactory.identityTransform(field)
        }
        return memberCheck to (statement as JooqField<Any>)
    }

    companion object {
        protected val defaultJooqSettings = Settings()
            // This makes sure we can use platform-specific functions (or UDFs)
            .withParseUnknownFunctions(ParseUnknownFunctions.IGNORE)
            // This follows the exact naming from the data policy's field names
            .withParseNameCase(ParseNameCase.AS_IS)
            // This ensures that we explicitly need to quote names
            .withRenderQuotedNames(RenderQuotedNames.EXPLICIT_DEFAULT_UNQUOTED)
    }
}
