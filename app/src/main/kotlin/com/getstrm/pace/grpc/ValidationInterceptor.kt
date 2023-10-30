package com.getstrm.pace.grpc

import build.buf.protovalidate.ValidationResult
import build.buf.protovalidate.Validator
import build.buf.protovalidate.exceptions.ValidationException
import com.getstrm.pace.exceptions.BadRequestException
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.BUG_REPORT
import com.google.protobuf.Message
import com.google.rpc.BadRequest
import com.google.rpc.BadRequest.FieldViolation
import com.google.rpc.DebugInfo
import io.grpc.*
import org.slf4j.LoggerFactory


class ValidationInterceptor : ServerInterceptor {
    private val validator = Validator()

    override fun <ReqT, RespT> interceptCall(
        serverCall: ServerCall<ReqT, RespT>,
        headers: Metadata?,
        serverCallHandler: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        return ValidationForwardingServerCallListener(
            serverCallHandler.startCall(serverCall, headers),
            serverCall,
            serverCall.methodDescriptor.fullMethodName,
            validator
        )
    }
}

class ValidationForwardingServerCallListener<ReqT, RespT>(
    delegate: ServerCall.Listener<ReqT>,
    private val serverCall: ServerCall<ReqT, RespT>,
    private val callName: String,
    private val validator: Validator
) : ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(delegate) {
    private var aborted = false


    override fun onMessage(message: ReqT) {

        try {
            val protoMessage = message as Message

            try {
                val result: ValidationResult = validator.validate(protoMessage)

                if (result.violations.isEmpty()) {
                    super.onMessage(message)
                } else {
                    aborted = true

                    val violations = result.violations.map {
                        FieldViolation.newBuilder()
                            .setField(it.fieldPath)
                            .setDescription("${it.message} (constraint id = ${it.constraintId})")
                            .build()
                    }

                    val badRequestException = BadRequestException(
                        BadRequestException.Code.INVALID_ARGUMENT,
                        BadRequest.newBuilder()
                            .addAllFieldViolations(violations)
                            .build(),
                        errorMessage = "Validation of message ${protoMessage.descriptorForType.name} failed, ${violations.size} violations found",
                    )

                    serverCall.close(badRequestException.status, badRequestException.trailers())
                }
            } catch (e: ValidationException) {
                aborted = true
                val internalException = internalException(protoMessage, e)
                serverCall.close(internalException.status, internalException.trailers())
            }
        } catch (e: Throwable) {
            aborted = true
            val internalException = internalException(null, e)
            serverCall.close(internalException.status, internalException.trailers())
        }
    }

    override fun onHalfClose() {
        if (!aborted) {
            super.onHalfClose()
        }
    }

    private fun internalException(
        protoMessage: Message?,
        e: Throwable
    ): InternalException {
        val protoMessageName = protoMessage?.descriptorForType?.name ?: "'Cannot determine Proto Message name'"
        log.error("An exception occurred while validating message $protoMessageName", e)

        return InternalException(
            InternalException.Code.INTERNAL,
            DebugInfo.newBuilder()
                .setDetail("Validation of message $protoMessageName failed, ${e.message}, $BUG_REPORT")
                .addAllStackEntries(e.stackTrace.map { it.toString() })
                .build()
        )
    }

    companion object {
        private val log by lazy { LoggerFactory.getLogger(ValidationForwardingServerCallListener::class.java) }
    }
}

