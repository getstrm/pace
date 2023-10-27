package com.getstrm.pace.common

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.util.headTailFold
import com.getstrm.pace.util.sqlDataType
import com.google.rpc.BadRequest
import org.jooq.Condition
import org.jooq.Field
import org.jooq.Queries
import org.jooq.Record
import org.jooq.SQLDialect
import org.jooq.SelectSelectStep
import org.jooq.conf.ParseNameCase
import org.jooq.conf.ParseUnknownFunctions
import org.jooq.conf.RenderQuotedNames
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.unquotedName
import org.jooq.impl.ParserException

typealias Principal = String

abstract class AbstractDynamicViewGenerator(
    protected val dataPolicy: DataPolicy,
    customJooqSettings: Settings.() -> Unit = {},
) {
    protected abstract fun List<Principal>.toPrincipalCondition(): Condition?

    protected open fun selectWithAdditionalHeaderStatements(fields: List<Field<*>>): SelectSelectStep<Record> =
        jooq.select(fields)

    protected open fun additionalFooterStatements(): Queries = DSL.queries()

    protected open fun renderName(name: String): String = jooq.renderNamedParams(name(name))

    protected val jooq = DSL.using(SQLDialect.DEFAULT, defaultJooqSettings.apply(customJooqSettings))
    protected val parser = jooq.parser()

    fun toDynamicViewSQL(): String {
        val queries = dataPolicy.ruleSetsList.map { ruleSet ->
            val targetView = ruleSet.target.fullname

            jooq.createOrReplaceView(renderName(targetView)).`as`(
                selectWithAdditionalHeaderStatements(
                    dataPolicy.source.attributesList.map { attribute ->
                        toField(
                            attribute,
                            ruleSet.fieldTransformsList.firstOrNull {
                                it.attribute.fullName() == attribute.fullName()
                            },
                        )
                    },
                )
                    .from(renderName(dataPolicy.source.ref))
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

    fun toField(
        attribute: DataPolicy.Attribute,
        fieldTransform: DataPolicy.RuleSet.FieldTransform?,
    ): Field<*> {
        if (fieldTransform == null) {
            // If there is no transform, we return just the field path (joined by a dot for now)
            return field(attribute.fullName())
        }
        if (fieldTransform.transformsList.size == 1) {
            // If there is only one transform it should be the only option
            val (_, queryPart) = toCase(fieldTransform.transformsList.last(), attribute)
            return queryPart.`as`(attribute.fullName())
        }

        val caseWhenStatement = fieldTransform.transformsList.headTailFold(
            headOperation = { transform ->
                val (c, q) = toCase(transform, attribute)
                DSL.`when`(c, q)
            },
            bodyOperation = { conditionStep, transform ->
                val (c, q) = toCase(transform, attribute)
                conditionStep.`when`(c, q)
            },
            tailOperation = { conditionStep, transform ->
                val (c, q) = toCase(transform, attribute)
                conditionStep.otherwise(q).`as`(attribute.fullName())
            },
        )

        return caseWhenStatement
    }

    fun toCase(
        transform: DataPolicy.RuleSet.FieldTransform.Transform?,
        attribute: DataPolicy.Attribute,
    ): Pair<Condition?, Field<Any>> {
        val memberCheck = transform?.principalsList?.toPrincipalCondition()

        val statement = when (transform?.transformCase) {
            DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.REGEX -> {
                // TODO regex validation
                if (transform.regex.replacement.isNullOrEmpty()) {
                    field(
                        "regexp_extract({0}, {1})",
                        String::class.java,
                        unquotedName(attribute.fullName()),
                        DSL.`val`(transform.regex.regex),
                    )
                } else {
                    DSL.regexpReplaceAll(
                        field(attribute.fullName(), String::class.java),
                        transform.regex.regex,
                        transform.regex.replacement,
                    )
                }
            }

            DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.FIXED -> {
                DSL.inline(transform.fixed.value, attribute.sqlDataType())
            }

            DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.HASH -> {
                field(
                    "hash({0}, {1})",
                    Any::class.java,
                    DSL.`val`(transform.hash.seed),
                    unquotedName(attribute.fullName()),
                )
            }

            DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.SQL_STATEMENT -> {
                try {
                    parser.parseField(transform.sqlStatement.statement)
                } catch (e: ParserException) {
                    throw invalidSqlStatementException(e)
                }
                // Todo: for now we use the parser just to detect errors, since the resulting sql may be incompatible with the target platform -> I've asked a question on SO: https://stackoverflow.com/q/77300702
                // (For example, the BigQuery string datatype gets parsed as varchar)
                // Doing this may reduce (sql injection) safety
                field(transform.sqlStatement.statement)
            }

            DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.NULLIFY -> DSL.inline<Any>(null)

            DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.TRANSFORM_NOT_SET, DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.IDENTITY, null -> {
                field(
                    attribute.fullName(),
                )
            }
        }
        return memberCheck to (statement as Field<Any>)
    }

    private fun invalidSqlStatementException(e: ParserException) = BadRequestException(
        BadRequestException.Code.INVALID_ARGUMENT,
        BadRequest.newBuilder()
            .addAllFieldViolations(
                listOf(
                    BadRequest.FieldViolation.newBuilder()
                        .setField("dataPolicy.ruleSetsList.fieldTransformsList.sqlStatement")
                        .setDescription("Error parsing SQL statement: ${e.message}")
                        .build()
                )
            )
            .build(),
        e
    )

    private fun DataPolicy.Attribute.fullName(): String = this.pathComponentsList.joinToString(".")

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
