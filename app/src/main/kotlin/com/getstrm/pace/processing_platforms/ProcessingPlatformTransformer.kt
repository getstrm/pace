package com.getstrm.pace.processing_platforms

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform.*
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.InternalException
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
import org.jooq.Field as JooqField

/**
 * Factory for creating jOOQ fields from [DataPolicy.Field]s and [DataPolicy.RuleSet.FieldTransform.Transform]s.
 * Functions can be overridden to support platform specific implementations of transforms.
 */
class ProcessingPlatformTransformer {
    fun regexpReplace(
        field: DataPolicy.Field,
        regexp: Regexp
    ): JooqField<*> =
        if (regexp.replacement.isNullOrEmpty()) {
            DSL.field(
                "regexp_extract({0}, {1})",
                String::class.java,
                DSL.unquotedName(field.fullName()),
                DSL.`val`(regexp.regexp),
            )
        } else {
            DSL.regexpReplaceAll(
                DSL.field(field.fullName(), String::class.java),
                regexp.regexp,
                regexp.replacement,
            )
        }

    fun fixed(
        field: DataPolicy.Field,
        fixed: Fixed
    ): JooqField<*> = DSL.inline(fixed.value, field.sqlDataType()).also {
        fixedDataTypeMatchesFieldType(fixed.value, field)
    }

    fun hash(
        field: DataPolicy.Field,
        hash: Hash
    ): JooqField<*> = DSL.field(
        "hash({0}, {1})",
        Any::class.java,
        DSL.`val`(hash.seed),
        DSL.unquotedName(field.fullName()),
    )

    fun sqlStatement(
        parser: Parser,
        sqlStatement: SqlStatement
    ): JooqField<*> {
        try {
            parser.parseField(sqlStatement.statement)
        } catch (e: ParserException) {
            throw invalidSqlStatementException(e)
        }
        // Todo: for now we use the parser just to detect errors, since the resulting sql may be incompatible with the target platform -> I've asked a question on SO: https://stackoverflow.com/q/77300702
        // (For example, the BigQuery string datatype gets parsed as varchar)
        // Doing this may reduce (sql injection) safety
        return DSL.field(sqlStatement.statement)
    }

    fun nullify(): JooqField<*> = DSL.inline<Any>(null)

    fun identity(field: DataPolicy.Field): JooqField<*> = DSL.field(field.fullName())

    fun detokenize(
        field: DataPolicy.Field,
        detokenize: Detokenize,
        renderedTokenSourceRefName: String,
        renderedSourceRefName: String
    ) = DSL.field(
        "coalesce({0}, {1})",
        String::class.java,
        DSL.unquotedName("$renderedTokenSourceRefName.${detokenize.valueField.fullName()}"),
        DSL.unquotedName("$renderedSourceRefName.${field.fullName()}"),
    )

    companion object {
        private val typeParser: TypeParser = TypeParser.newBuilder().build()

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
    }
}
