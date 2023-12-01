package com.getstrm.pace.grpc

import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.PaceStatusException.Companion.BUG_REPORT
import com.getstrm.pace.exceptions.ProtoValidator
import com.google.protobuf.Message
import com.google.rpc.DebugInfo
import io.grpc.*
import org.slf4j.LoggerFactory


class ValidationInterceptor : ServerInterceptor {
    override fun <ReqT, RespT> interceptCall(
        serverCall: ServerCall<ReqT, RespT>,
        headers: Metadata?,
        serverCallHandler: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        return ValidationForwardingServerCallListener(
            serverCallHandler.startCall(serverCall, headers),
            serverCall
        )
    }
}

class ValidationForwardingServerCallListener<ReqT, RespT>(
    delegate: ServerCall.Listener<ReqT>,
    private val serverCall: ServerCall<ReqT, RespT>
) : ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(delegate) {
    private var aborted = false


    override fun onMessage(message: ReqT) {
        try {
            val protoMessage = message as Message
            val validateError = ProtoValidator.validate(protoMessage)

            if (validateError != null) {
                aborted = true
                serverCall.close(validateError.status, validateError.trailers())
            } else {
                super.onMessage(message)
            }
        } catch (e: Throwable) {
            aborted = true
            val protoMessageName = "'Cannot determine Proto Message name'"
            log.error("An exception occurred while validating message $protoMessageName", e)
            val internalException = InternalException(
                InternalException.Code.INTERNAL,
                DebugInfo.newBuilder()
                    .setDetail("Validation of message $protoMessageName failed, ${e.message}, $BUG_REPORT")
                    .addAllStackEntries(e.stackTrace.map { it.toString() })
                    .build()
            )
            serverCall.close(internalException.status, internalException.trailers())
        }
    }

    override fun onHalfClose() {
        if (!aborted) {
            super.onHalfClose()
        }
    }

    companion object {
        private val log by lazy { LoggerFactory.getLogger(ValidationForwardingServerCallListener::class.java) }
    }
}

