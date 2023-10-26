package com.getstrm.pace.util

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.getstrm.pace.exceptions.PaceStatusException
import com.google.cloud.bigquery.Table
import com.google.cloud.bigquery.TableId
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.Timestamp
import com.google.protobuf.util.JsonFormat
import com.google.protobuf.util.Timestamps
import org.jooq.*
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy.RuleSet.FieldTransform as ApiFieldTransform
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform as ApiTransform

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

fun GeneratedMessageV3.toJsonb(): JSONB {
    return JSONB.valueOf(toJson())
}

fun GeneratedMessageV3.toJson(): String = JsonFormat.printer()
    .omittingInsignificantWhitespace()
    .print(this)

fun GeneratedMessageV3.toJsonWithDefaults(): String = JsonFormat.printer()
    .omittingInsignificantWhitespace()
    .includingDefaultValueFields()
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

fun String.parseTransforms(): List<ApiTransform> = let {
    val builder = ApiFieldTransform.newBuilder()
    JsonFormat.parser().merge(this.yaml2json(), builder)
    builder.build().transformsList
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

fun DataPolicy.Attribute.pathString() = this.pathComponentsList.joinToString()

fun <T, Accumulator, Result> List<T>.headTailFold(
    headOperation: (T) -> Accumulator,
    bodyOperation: (Accumulator, T) -> Accumulator,
    tailOperation: (Accumulator, T) -> Result,
): Result {
    var accumulator = headOperation(this.first())
    for (element in this.drop(1).dropLast(1)) {
        accumulator = bodyOperation(accumulator, element)
    }
    return tailOperation(accumulator, this.last())
}

fun DataPolicy.Attribute.sqlDataType(): DataType<*> =
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

fun DataPolicy.Attribute.normalizeType(): DataPolicy.Attribute =
    toBuilder().setType( sqlDataType().typeName).build()

val sqlParser = DSL.using(SQLDialect.DEFAULT).parser()
