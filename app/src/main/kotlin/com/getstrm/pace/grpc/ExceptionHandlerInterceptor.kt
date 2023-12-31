package com.getstrm.pace.grpc

import com.getstrm.pace.exceptions.PaceStatusException
import io.grpc.*
import java.net.InetAddress
import java.util.*
import org.slf4j.LoggerFactory

class ExceptionHandlerInterceptor(private val exposeExceptions: Boolean) : ServerInterceptor {
    override fun <ReqT, RespT> interceptCall(
        serverCall: ServerCall<ReqT, RespT>,
        headers: Metadata?,
        serverCallHandler: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        return serverCallHandler.startCall(
            ExceptionTranslatingServerCall(serverCall, exposeExceptions),
            headers
        )
    }

    class ExceptionTranslatingServerCall<ReqT, RespT>(
        private val delegate: ServerCall<ReqT, RespT>,
        private val exposeExceptions: Boolean,
    ) : ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(delegate) {

        private val instanceId: String = InetAddress.getLocalHost().hostName

        override fun close(status: Status, trailers: Metadata) {
            /**
             * Examples of status codes that can be directly returned to the caller:
             * - OK
             * - CANCELLED
             * - NOT_FOUND
             * - ALREADY_EXISTS
             * - ?
             *
             * [unexpectedExceptionStatuses] contain all status codes that need error handling
             * logic.
             */
            if (status.code !in unexpectedExceptionStatuses) {
                return delegate.close(status, trailers)
            }

            val cause = status.cause
            val callName = delegate.methodDescriptor.fullMethodName
            val (traceId, traceIdExists) = getOrGenerateTraceId(status)

            val newStatus =
                if (cause is PaceStatusException) {
                    val extraTrailers =
                        cause.trailers(
                            mapOf(
                                "callName" to callName,
                                "traceId" to traceId,
                                "instanceId" to instanceId
                            )
                        )
                    trailers.merge(extraTrailers)

                    cause.status.withDescription(cause.message)
                } else {
                    if (exposeExceptions) {
                            Status.INTERNAL.withDescription(
                                (status.description ?: status.cause?.message).let {
                                    if (traceIdExists) "[$instanceId] $it"
                                    else "[$instanceId] $traceId $it"
                                },
                            )
                        } else {
                            Status.INTERNAL.withDescription(
                                "An error occurred in call $callName. Error ID: $traceId."
                            )
                        }
                        .withCause(null)
                }

            if (cause !is PaceStatusException) {
                val messageElements =
                    mutableListOf(
                        "Call=$callName",
                        "Trace ID=$traceId",
                        "Server ID=$instanceId",
                    )

                log.error(
                    "Unexpected error [${messageElements.joinToString(", ")}]",
                    cause,
                )
            }

            delegate.close(newStatus, trailers)
        }
    }

    companion object {
        private val log by lazy { LoggerFactory.getLogger(ExceptionHandlerInterceptor::class.java) }

        private const val errorIdPrefix = "PACE-"

        // Regex pattern for UUIDv4
        private val errorIdRegex =
            ".*($errorIdPrefix[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}).*"
                .toRegex(RegexOption.IGNORE_CASE)

        private val unexpectedExceptionStatuses =
            setOf(Status.Code.UNKNOWN, Status.Code.INTERNAL, Status.Code.UNAVAILABLE)

        private fun getOrGenerateTraceId(status: Status) =
            status.description?.let { description ->
                val match = errorIdRegex.find(description)

                if (match != null) {
                    val (id) = match.destructured
                    id to true
                } else {
                    val id = randomUniqueId()
                    log.error(
                        "Tried to extract an PACE-<UUIDv4> from a response with status code INTERNAL, but failed " +
                            "because no match was found. Falling back to id '{}'. Status code description: {}",
                        id,
                        description,
                    )
                    id to true
                }
            } ?: (randomUniqueId() to false)

        private fun randomUniqueId() = "$errorIdPrefix${UUID.randomUUID()}"
    }
}
