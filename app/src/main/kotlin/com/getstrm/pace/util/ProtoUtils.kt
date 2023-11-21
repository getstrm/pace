package com.getstrm.pace.util

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import build.buf.gen.getstrm.pace.api.global_transforms.v1alpha.ListGlobalTransformsResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.getstrm.jooq.generated.tables.records.DataPoliciesRecord
import com.getstrm.jooq.generated.tables.records.GlobalTransformsRecord
import com.getstrm.pace.exceptions.BadRequestException
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.Message
import com.google.protobuf.Timestamp
import com.google.protobuf.util.JsonFormat
import com.google.protobuf.util.Timestamps
import com.google.rpc.BadRequest
import org.jooq.JSONB
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset


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

fun DataPoliciesRecord.toApiDataPolicy(): DataPolicy = this.policy!!.let {
    with(DataPolicy.newBuilder()) {
        JsonFormat.parser().ignoringUnknownFields().merge(it.data(), this)
        build()
    }
}

fun DataPolicy.Field.pathString() = this.namePartsList.joinToString(separator = ".")

fun GlobalTransformsRecord.toGlobalTransform() = GlobalTransform.newBuilder().merge(this.transform!!).build()

fun DataPolicy.Field.fullName(): String = this.namePartsList.joinToString(".")

/** force case type string to TransformCase
 * don't allow TRANSFORM_NOT_SET
 */
fun String.toTransformCase(): GlobalTransform.TransformCase = let {
    GlobalTransform.TransformCase.valueOf(this).also {
        if (it == GlobalTransform.TransformCase.TRANSFORM_NOT_SET) {
            throw BadRequestException(
                code = BadRequestException.Code.INVALID_ARGUMENT,
                badRequest = BadRequest.newBuilder()
                    .addFieldViolations(
                        BadRequest.FieldViolation.newBuilder()
                            .setDescription(
                                "type '${this}' is not in ${
                                    GlobalTransform.TransformCase.values().joinToString()
                                } "
                            )
                            .build()
                    )
                    .build()
            )
        }
    }
}

fun GlobalTransform.refAndType(): Pair<String, GlobalTransform.TransformCase> = Pair(
    when (transformCase) {
        GlobalTransform.TransformCase.TAG_TRANSFORM -> tagTransform.tagContent
        null, GlobalTransform.TransformCase.TRANSFORM_NOT_SET -> throw BadRequestException(
            code = BadRequestException.Code.INVALID_ARGUMENT,
            badRequest = BadRequest.newBuilder()
                .addFieldViolations(
                    BadRequest.FieldViolation.newBuilder()
                        .setDescription("A GlobalTransform was seen with no `transform`. The GlobalTransform was: " + this.toJson())
                        .build()
                )
                .build()
        )
    }, transformCase
)
