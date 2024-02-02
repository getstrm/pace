package com.getstrm.pace.exceptions

import build.buf.protovalidate.ValidationResult
import build.buf.protovalidate.Validator
import build.buf.protovalidate.exceptions.ValidationException
import com.google.protobuf.Message
import com.google.rpc.BadRequest
import com.google.rpc.DebugInfo
import org.slf4j.LoggerFactory

object ProtoValidator {
    private val log by lazy { LoggerFactory.getLogger(javaClass) }
    private val validator = Validator()

    fun validate(message: Message): PaceStatusException? {
        return try {
            val result: ValidationResult = validator.validate(message)

            if (result.violations.isEmpty()) {
                null
            } else {
                val violations =
                    result.violations.map {
                        BadRequest.FieldViolation.newBuilder()
                            .setField(if (it.forKey) "${it.fieldPath} (map key)" else it.fieldPath)
                            .setDescription("${it.message} (constraint id = ${it.constraintId})")
                            .build()
                    }

                BadRequestException(
                    BadRequestException.Code.INVALID_ARGUMENT,
                    BadRequest.newBuilder().addAllFieldViolations(violations).build(),
                    errorMessage =
                        "Validation of message ${message.descriptorForType.name} failed, ${violations.size} violations found",
                )
            }
        } catch (e: ValidationException) {
            val protoMessageName =
                message.descriptorForType.name ?: "'Cannot determine Proto Message name'"
            log.error("An exception occurred while validating message $protoMessageName", e)
            InternalException(
                InternalException.Code.INTERNAL,
                DebugInfo.newBuilder()
                    .setDetail(
                        "Validation of message $protoMessageName failed, ${e.message}, ${PaceStatusException.BUG_REPORT}"
                    )
                    .addAllStackEntries(e.stackTrace.map { it.toString() })
                    .build()
            )
        }
    }
}
