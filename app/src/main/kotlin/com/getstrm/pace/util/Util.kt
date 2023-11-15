package com.getstrm.pace.util

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform.TransformCase
import build.buf.gen.getstrm.pace.api.global_transforms.v1alpha.ListGlobalTransformsResponse
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.getstrm.jooq.generated.tables.records.GlobalTransformsRecord
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.PaceStatusException
import com.google.cloud.bigquery.Table
import com.google.cloud.bigquery.TableId
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.Message
import com.google.protobuf.Timestamp
import com.google.protobuf.util.JsonFormat
import com.google.protobuf.util.Timestamps
import com.google.rpc.BadRequest
import org.jooq.*
import org.jooq.conf.ParseNameCase
import org.jooq.conf.ParseUnknownFunctions
import org.jooq.conf.RenderQuotedNames
import org.jooq.conf.Settings
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private val log by lazy { LoggerFactory.getLogger("Util") }

suspend fun <R> coUnwrapStatusException(block: suspend () -> R): R {
    try {
        return block()
    } catch (dae: DataAccessException) {
        val strmStatusException = getFirstPaceStatusException(dae)
        if (strmStatusException != null) throw strmStatusException else throw dae
    }
}

private fun getFirstPaceStatusException(throwable: Throwable): PaceStatusException? {
    if (throwable is PaceStatusException) {
        return throwable
    }
    return throwable.cause?.let { getFirstPaceStatusException(it) }
}

fun <T : Message.Builder> T.merge(jsonb: JSONB): T =
    this.also {
        JsonFormat.parser()
            .ignoringUnknownFields()
            .merge(
                jsonb.data(),
                this,
            )
    }

fun GeneratedMessageV3.toJsonbWithDefaults(): JSONB {
    return JSONB.valueOf(toJsonWithDefaults())
}

fun GeneratedMessageV3.toJson(): String = JsonFormat.printer()
    .omittingInsignificantWhitespace()
    .preservingProtoFieldNames()
    .print(this)

fun GeneratedMessageV3.toJsonWithDefaults(): String = JsonFormat.printer()
    .omittingInsignificantWhitespace()
    .includingDefaultValueFields()
    .preservingProtoFieldNames()
    .print(this)

fun String.yaml2json(): String {
    val yamlReader = ObjectMapper(YAMLFactory())
    return try {
        val obj = yamlReader.readValue(this, Any::class.java)
        val jsonWriter = ObjectMapper()
        jsonWriter.writeValueAsString(obj)
    } catch (e: JsonProcessingException) {
        throw RuntimeException(e)
    }
}

fun GeneratedMessageV3.toYaml(): String =
    ObjectMapper(YAMLFactory()).writeValueAsString(ObjectMapper().readTree(toJsonWithDefaults()))

fun String.parseDataPolicy(): DataPolicy = let {
    val builder = DataPolicy.newBuilder()
    JsonFormat.parser().ignoringUnknownFields().merge(this, builder)
    builder.build()
}

fun String.parseTransforms(): List<GlobalTransform> = let {
    val builder = ListGlobalTransformsResponse.newBuilder()
    JsonFormat.parser().merge(this.yaml2json(), builder)
    builder.build().globalTransformsList
}

fun String.parseTransform(): GlobalTransform = let {
    val builder = GlobalTransform.newBuilder()
    JsonFormat.parser().ignoringUnknownFields().merge(this, builder)
    builder.build()
}

fun Long.toTimestamp(): Timestamp {
    val offsetDateTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
    return Timestamp.newBuilder()
        .setSeconds(offsetDateTime.toEpochSecond())
        .setNanos(offsetDateTime.nano)
        .build()
}

fun Timestamp.toOffsetDateTime(): OffsetDateTime = Instant.ofEpochMilli(Timestamps.toMillis(this)).toOffsetDateTime()

fun Instant.toOffsetDateTime() = this.atOffset(ZoneOffset.UTC)

fun OffsetDateTime.toTimestamp() = Timestamp.newBuilder()
    .setSeconds(toEpochSecond())
    .setNanos(nano)
    .build()

fun Table.toFullName() = tableId.toFullName()

fun TableId.toFullName() = "$project.$dataset.$table"

fun DataPolicy.Field.pathString() = this.namePartsList.joinToString(separator = ".")

fun GlobalTransformsRecord.toGlobalTransform() = GlobalTransform.newBuilder().merge(this.transform!!).build()

fun DataPolicy.Field.fullName(): String = this.namePartsList.joinToString(".")

val defaultJooqSettings: Settings = Settings()
    // This makes sure we can use platform-specific functions (or UDFs)
    .withParseUnknownFunctions(ParseUnknownFunctions.IGNORE)
    // This follows the exact naming from the data policy's field names
    .withParseNameCase(ParseNameCase.AS_IS)
    // This ensures that we explicitly need to quote names
    .withRenderQuotedNames(RenderQuotedNames.EXPLICIT_DEFAULT_UNQUOTED)

/**
 * Apply different operations on the head, tail and body of a collection. The head and tail contain a single element,
 * and the body contains the rest of the elements in the collection. Requires at least 2 elements in the collection.
 *
 * @param headOperation Operation to apply on the first element
 * @param bodyOperation Operation to apply on all elements except the first and last
 * @param tailOperation Operation to apply on the last element
 */
fun <T, Accumulator, Result> List<T>.headTailFold(
    headOperation: (T) -> Accumulator,
    bodyOperation: (Accumulator, T) -> Accumulator,
    tailOperation: (Accumulator, T) -> Result,
): Result {
    require(this.size >= 2) { "List must have at least 2 elements" }
    var accumulator = headOperation(this.first())
    for (element in this.drop(1).dropLast(1)) {
        accumulator = bodyOperation(accumulator, element)
    }
    return tailOperation(accumulator, this.last())
}

fun DataPolicy.Field.sqlDataType(): DataType<*> =
    try {
        if (type.lowercase() == "struct") {
            SQLDataType.RECORD
        } else {
            sqlParser.parseField("a::$type").dataType.sqlDataType!!
        }
    } catch (e: Exception) {
        log.warn("Can't parse {}, default to VARCHAR", type)
        SQLDataType.VARCHAR
    }

fun DataPolicy.Field.normalizeType(): DataPolicy.Field =
    toBuilder().setType(sqlDataType().typeName).build()

val sqlParser = DSL.using(SQLDialect.DEFAULT).parser()

/** force case type string to TransformCase
 * don't allow TRANSFORM_NOT_SET
 */
fun String.toTransformCase(): TransformCase = let {
    TransformCase.valueOf(this).also {
        if (it == TransformCase.TRANSFORM_NOT_SET) {
            throw BadRequestException(
                code = BadRequestException.Code.INVALID_ARGUMENT,
                badRequest = BadRequest.newBuilder()
                    .addFieldViolations(
                        BadRequest.FieldViolation.newBuilder()
                            .setDescription(
                                "type '${this}' is not in ${
                                    TransformCase.values().joinToString()
                                } "
                            )
                            .build()
                    )
                    .build()
            )
        }
    }
}

fun GlobalTransform.refAndType(): Pair<String, TransformCase> = Pair(when(transformCase){
    TransformCase.TAG_TRANSFORM -> tagTransform.tagContent
    TransformCase.TRANSFORM_NOT_SET -> TODO()
}, transformCase)
