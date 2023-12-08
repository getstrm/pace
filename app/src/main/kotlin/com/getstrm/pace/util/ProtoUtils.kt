package com.getstrm.pace.util

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.GlobalTransform
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.ProtoValidator
import com.google.protobuf.Descriptors
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.Message
import com.google.protobuf.Timestamp
import com.google.protobuf.util.JsonFormat
import com.google.protobuf.util.Timestamps
import com.google.rpc.BadRequest
import com.google.rpc.DebugInfo
import org.jooq.JSONB
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*
import kotlin.reflect.jvm.javaConstructor

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

/**
 * Convert a Proto Message to a JSON string, and exclude default values (Proto default values are not included by default)
 */
fun GeneratedMessageV3.toJson(): String = JsonFormat.printer()
    .omittingInsignificantWhitespace()
    .preservingProtoFieldNames()
    .print(this)


/**
 * Convert a Proto Message to a JSON string, and include default values (Proto default values are not included by default)
 */
fun GeneratedMessageV3.toJsonWithDefaults(): String = JsonFormat.printer()
    .omittingInsignificantWhitespace()
    .includingDefaultValueFields()
    .preservingProtoFieldNames()
    .print(this)

/**
 * Convert a Proto Message to a YAML string, and include default values (Proto default values are not included by default)
 */
fun GeneratedMessageV3.toYaml(): String =
    YAML_MAPPER.writeValueAsString(JSON_MAPPER.readTree(toJsonWithDefaults()))

/**
 * Validate the message using the Protovalidate options that were configured for this message.
 */
fun Message.validate() {
    ProtoValidator.validate(this)?.let { throw it }
}

/**
 * Accepts JSON, YAML, or base64 encoded JSON or YAML, and converts it into a Proto Message of type [T]
 */
inline fun <reified T : GeneratedMessageV3> String.toProto(validate: Boolean = true): T {
    // To be able to create a builder from type T, we need an instance of T
    // As the constructors of generated proto messages are private, we need to set it accessible first
    val constructor = T::class.constructors.first { it.parameters.isEmpty() }
    constructor.javaConstructor?.trySetAccessible()
    val builder = constructor.call().toBuilder()
    JsonFormat.parser().ignoringUnknownFields().merge(toJsonString(), builder)
    return (builder.build() as T).also { if (validate) it.validate() }
}

/**
 * Accepts JSON, YAML, or base64 encoded JSON or YAML
 * and converts it to a JSON string
 */
fun String.toJsonString(): String {
    return try {
        when {
            // JSON
            this.startsWith("{") -> this
            // YAML
            this.contains("\n") -> this.yamlToJson(true)!! // null is only returned upon exceptions
            // Base64 encoded JSON or YAML
            else -> Base64.getDecoder().decode(this).toString(Charsets.UTF_8).toJsonString()
        }
    } catch (e: Exception) {
        throw BadRequestException(
            BadRequestException.Code.INVALID_ARGUMENT,
            BadRequest.newBuilder()
                .addFieldViolations(
                    BadRequest.FieldViolation.newBuilder()
                        .setDescription("Could not parse payload as JSON, YAML or base64 encoded JSON or YAML: ${e.message}")
                        .build()
                )
                .build()
        )
    }
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

fun DataPolicy.Field.pathString() = this.namePartsList.joinToString(separator = ".")

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
                                    GlobalTransform.TransformCase.entries.joinToString()
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

fun Descriptors.Descriptor.getJSONSchema(): String {
    val directory = this.fullName.replace(".${this.name}", "")
    val file = "${this.name}.json"

    return object {}.javaClass.getResource("/jsonschema/$directory/$file")?.readText() ?: throw InternalException(
        InternalException.Code.INTERNAL,
        DebugInfo.newBuilder()
            .setDetail("Could not load JSON Schema for ${this.fullName}")
            .build()
    )
}

fun DataPolicy.Source.toDDL(): String {
    val fields = this.fieldsList.joinToString(separator = ",\n") { field ->
        val name = field.namePartsList.joinToString(separator = ".")

        "$name ${field.type} ${if (field.required) "NOT NULL" else ""}"
    }

    return """
        CREATE TABLE ${this.ref} (
            $fields
        )
    """.trimIndent()
}
