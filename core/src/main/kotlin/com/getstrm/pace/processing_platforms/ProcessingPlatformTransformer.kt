package com.getstrm.pace.processing_platforms

import org.jooq.Field as JooqField
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Aggregation
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Detokenize
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Fixed
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Hash
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.NumericRounding
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.Regexp
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.SqlStatement
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.BUG_REPORT
import com.getstrm.pace.util.fullName
import com.getstrm.pace.util.sqlDataType
import com.github.drapostolos.typeparser.TypeParser
import com.github.drapostolos.typeparser.TypeParserException
import com.google.rpc.BadRequest
import com.google.rpc.DebugInfo
import org.jooq.Parser
import org.jooq.impl.DSL
import org.jooq.impl.ParserException
import org.jooq.impl.SQLDataType

val CAPTURING_GROUP_REGEX = Regex("""\$(\d+)""")

/**
 * Factory for creating jOOQ fields from [DataPolicy.Field]s and
 * [DataPolicy.RuleSet.FieldTransform.Transform]s. Functions can be overridden to support platform
 * specific implementations of transforms.
 */
interface ProcessingPlatformTransformer : ProcessingPlatformRenderer {

    fun regexpReplace(field: DataPolicy.Field, regexp: Regexp): JooqField<*> =
        if (regexp.replacement.isNullOrEmpty()) {
            DSL.field(
                "regexp_extract({0}, {1})",
                String::class.java,
                renderName(field.fullName()),
                DSL.`val`(regexp.regexp),
            )
        } else {
            DSL.field(
                "regexp_replace({0}, {1}, {2})",
                String::class.java,
                renderName(field.fullName()),
                DSL.`val`(regexp.regexp),
                DSL.`val`(regexp.replacement),
            )
        }

    /**
     * Create a jOOQ field from a fixed value. If [implicitType] is true, the fixed value will be
     * used as is, without casting to the field's data type.
     */
    fun fixed(field: DataPolicy.Field, fixed: Fixed, implicitType: Boolean = false): JooqField<*> {
        return if (implicitType) {
            DSL.inlined(DSL.field(fixed.value))
        } else {
            DSL.inline(fixed.value, field.sqlDataType()).also {
                fixedDataTypeMatchesFieldType(fixed.value, field)
            }
        }
    }

    // Fixme: hash implementations differ significantly between platforms and are often limited to a
    // single (output) type. We may need casting etc.
    fun hash(field: DataPolicy.Field, hash: Hash): JooqField<*> =
        if (hash.hasSeed()) {
            DSL.field(
                "hash({0}, {1})",
                Any::class.java,
                DSL.`val`(hash.seed),
                renderName(field.fullName()),
            )
        } else {
            DSL.field(
                "hash({0})",
                Any::class.java,
                renderName(field.fullName()),
            )
        }

    fun sqlStatement(parser: Parser, sqlStatement: SqlStatement): JooqField<*> {
        try {
            parser.parseField(sqlStatement.statement)
        } catch (e: ParserException) {
            throw invalidSqlStatementException(e)
        }
        // Todo: for now we use the parser just to detect errors, since the resulting sql may be
        // incompatible with the target platform -> I've asked a question on SO:
        // https://stackoverflow.com/q/77300702
        // (For example, the BigQuery string datatype gets parsed as varchar)
        // Doing this may reduce (sql injection) safety
        return DSL.field(sqlStatement.statement)
    }

    fun nullify(): JooqField<*> = DSL.inline<Any>(null)

    fun identity(field: DataPolicy.Field): JooqField<*> = DSL.field(renderName(field.fullName()))

    fun detokenize(
        field: DataPolicy.Field,
        detokenize: Detokenize,
        sourceRef: String,
    ) =
        DSL.field(
            "coalesce({0}, {1})",
            String::class.java,
            DSL.unquotedName(
                "${renderName(detokenize.tokenSourceRef)}.${renderName(detokenize.valueField.fullName())}"
            ),
            DSL.unquotedName("${renderName(sourceRef)}.${renderName(field.fullName())}"),
        )

    fun numericRounding(field: DataPolicy.Field, numericRounding: NumericRounding): JooqField<*> =
        when (numericRounding.roundingCase) {
            NumericRounding.RoundingCase.CEIL ->
                DSL.ceil(
                        DSL.field(renderName(field.fullName()), Float::class.java)
                            .div(numericRounding.ceil.divisor)
                    )
                    .multiply(numericRounding.ceil.divisor)
            NumericRounding.RoundingCase.FLOOR ->
                DSL.floor(
                        DSL.field(renderName(field.fullName()), Float::class.java)
                            .div(numericRounding.floor.divisor)
                    )
                    .multiply(numericRounding.floor.divisor)
            NumericRounding.RoundingCase.ROUND -> {
                if (numericRounding.round.hasDivisor()) {
                    DSL.round(
                            DSL.field(renderName(field.fullName()), Float::class.java)
                                .div(numericRounding.round.divisor),
                            numericRounding.round.precision
                        )
                        .multiply(numericRounding.round.divisor)
                } else {
                    DSL.round(
                        DSL.field(renderName(field.fullName()), Float::class.java),
                        numericRounding.round.precision
                    )
                }
            }
            NumericRounding.RoundingCase.ROUNDING_NOT_SET,
            null ->
                throw InternalException(
                    InternalException.Code.INTERNAL,
                    DebugInfo.newBuilder()
                        .setDetail(
                            "Rounding type ${numericRounding.roundingCase} is not supported or not set. $BUG_REPORT"
                        )
                        .build()
                )
        }

    fun aggregation(field: DataPolicy.Field, aggregation: Aggregation): JooqField<*> {
        val jooqField = DSL.field(renderName(field.fullName()), Float::class.java)

        val jooqAggregation =
            when (aggregation.aggregationTypeCase) {
                Aggregation.AggregationTypeCase.SUM -> DSL.sum(jooqField)
                Aggregation.AggregationTypeCase.AVG ->
                    DSL.avg(
                        aggregation.avg.castTo
                            .ifEmpty { null }
                            ?.let {
                                DSL.field(
                                    "cast({0} as {1})",
                                    Float::class.java,
                                    jooqField,
                                    DSL.unquotedName(it)
                                )
                            } ?: DSL.cast(jooqField, SQLDataType.DECIMAL)
                    )
                Aggregation.AggregationTypeCase.MIN -> DSL.min(jooqField)
                Aggregation.AggregationTypeCase.MAX -> DSL.max(jooqField)
                else ->
                    throw InternalException(
                        InternalException.Code.INTERNAL,
                        DebugInfo.newBuilder()
                            .setDetail(
                                "Aggregation type ${aggregation.aggregationTypeCase.name} is not supported or does not exist. ${PaceStatusException.UNIMPLEMENTED}"
                            )
                            .build()
                    )
            }
        val aggregationField =
            DSL.field(
                "{0} over({1})",
                Float::class.java,
                jooqAggregation,
                DSL.partitionBy(
                    aggregation.partitionByList.map { DSL.field(renderName(it.fullName())) }
                )
            )

        return aggregation.avg
            ?.takeIf { it.hasPrecision() }
            ?.let { DSL.round(aggregationField, it.precision) } ?: aggregationField
    }

    companion object {
        private val typeParser: TypeParser = TypeParser.newBuilder().build()

        private fun fixedDataTypeMatchesFieldType(fixedValue: String, field: DataPolicy.Field) {
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
                                    .setDescription(
                                        "Data type of fixed value provided for field ${field.fullName()} does not match the data type of the field"
                                    )
                                    .build()
                            )
                        )
                        .build(),
                    e
                )
            }
        }

        private fun invalidSqlStatementException(e: ParserException) =
            BadRequestException(
                BadRequestException.Code.INVALID_ARGUMENT,
                BadRequest.newBuilder()
                    .addAllFieldViolations(
                        listOf(
                            BadRequest.FieldViolation.newBuilder()
                                .setField(
                                    "dataPolicy.ruleSetsList.fieldTransformsList.sqlStatement"
                                )
                                .setDescription("Error parsing SQL statement: ${e.message}")
                                .build()
                        )
                    )
                    .build(),
                e
            )
    }
}

object DefaultProcessingPlatformTransformer : ProcessingPlatformTransformer
