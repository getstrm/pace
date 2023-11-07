package com.getstrm.pace.processing_platforms

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.util.headTailFold
import com.getstrm.pace.util.sqlDataType
import com.github.drapostolos.typeparser.TypeParser
import com.github.drapostolos.typeparser.TypeParserException
import com.google.rpc.BadRequest
import org.jooq.*
import org.jooq.conf.ParseNameCase
import org.jooq.conf.ParseUnknownFunctions
import org.jooq.conf.RenderQuotedNames
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.DSL.*
import org.jooq.impl.ParserException
import org.jooq.Field as JooqField

abstract class AbstractDynamicViewGenerator(
    protected val dataPolicy: DataPolicy,
    customJooqSettings: Settings.() -> Unit = {},
) {
    protected abstract fun List<DataPolicy.Principal>.toPrincipalCondition(): Condition?

    protected open fun selectWithAdditionalHeaderStatements(fields: List<JooqField<*>>): SelectSelectStep<Record> =
        jooq.select(fields)

    protected open fun additionalFooterStatements(): Queries = DSL.queries()

    protected open fun renderName(name: String): String = jooq.renderNamedParams(name(name))

    protected val jooq = DSL.using(SQLDialect.DEFAULT, defaultJooqSettings.apply(customJooqSettings))
    private val parser = jooq.parser()
    private val typeParser: TypeParser = TypeParser.newBuilder().build()

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
            DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.REGEXP -> {
                if (transform.regexp.replacement.isNullOrEmpty()) {
                    field(
                        "regexp_extract({0}, {1})",
                        String::class.java,
                        unquotedName(field.fullName()),
                        DSL.`val`(transform.regexp.regexp),
                    )
                } else
                    DSL.regexpReplaceAll(
                        field(field.fullName(), String::class.java),
                        transform.regexp.regexp,
                        transform.regexp.replacement,
                    )
            }

            DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.FIXED -> {
                fixedDataTypeMatchesFieldType(transform.fixed.value, field)
                DSL.inline(transform.fixed.value, field.sqlDataType())
            }

            DataPolicy.RuleSet.FieldTransform.Transform.TransformCase.HASH -> {
                field(
                    "hash({0}, {1})",
                    Any::class.java,
                    DSL.`val`(transform.hash.seed),
                    unquotedName(field.fullName()),
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
                field(field.fullName())
            }
        }
        return memberCheck to (statement as JooqField<Any>)
    }

    private fun fixedDataTypeMatchesFieldType(
        fixedValue: String,
        field: DataPolicy.Field
    ) {
        try {
            typeParser.parse(fixedValue, field.sqlDataType().type)
        } catch (e: TypeParserException) {
            throw BadRequestException(
                BadRequestException.Code.INVALID_ARGUMENT,
                BadRequest.newBuilder()
                    .addAllFieldViolations(
                        listOf(
                            BadRequest.FieldViolation.newBuilder()
                                .setField("dataPolicy.ruleSetsList.fieldTransformsList.fixed")
                                .setDescription("Data type of fixed value provided for field ${field.fullName()} does not match the data type of the field")
                                .build()
                        )
                    )
                    .build(),
                e
            )
        }
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

    private fun DataPolicy.Field.fullName(): String = this.namePartsList.joinToString(".")

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
