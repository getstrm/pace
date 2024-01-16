package com.getstrm.pace.exceptions

import com.google.protobuf.Any
import com.google.rpc.*
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.protobuf.StatusProto

/**
 * Base class for all exceptions that should be caught by the ExceptionHandlerInterceptor. This
 * construct follows Google API design patterns. See here for more mappings:
 * https://cloud.google.com/apis/design/errors#error_payloads
 */
sealed class PaceStatusException(val status: Status) : Exception(status.cause) {
    companion object {
        const val BUG_REPORT =
            "This is a bug, please report it to https://github.com/getstrm/pace/issues/new"
        const val UNIMPLEMENTED =
            "This is an unimplemented feature, please check whether a feature request is present or create a feature request: https://github.com/getstrm/pace/issues"
    }

    /**
     * Returns the trailers that should be sent to the client, which sets the [Metadata.Key] with
     * identifier grpc-status-details-bin, to support clients that are able to show rich error
     * messages.
     *
     * @param errorInfoMetadata additional metadata for error details of type [ErrorInfo],
     *   effectively only for [ClientErrorException]s. This is ignored for all other exceptions.
     */
    fun trailers(errorInfoMetadata: Map<String, String> = emptyMap()): Metadata {
        val details: Any =
            when (this) {
                is BadRequestException -> Any.pack(badRequest)
                is ResourceException -> Any.pack(resourceInfo)
                is ClientErrorException ->
                    Any.pack(
                        errorInfo.toBuilder().apply { putAllMetadata(errorInfoMetadata) }.build()
                    )
                is InternalException -> Any.pack(debugInfo)
                is PreconditionFailedException -> Any.pack(preconditionFailure)
                is QuotaFailureException -> Any.pack(quotaFailure)
            }

        val richStatus =
            com.google.rpc.Status.newBuilder()
                .setCode(status.code.value())
                .setMessage(status.description ?: message ?: "")
                .addDetails(details)

        return StatusProto.toStatusRuntimeException(richStatus.build()).trailers ?: Metadata()
    }
}

class ResourceException(
    val code: Code,
    val resourceInfo: ResourceInfo,
    cause: Throwable? = null,
    errorMessage: String? = null
) :
    PaceStatusException(
        code.status
            .withDescription(resourceInfo.description ?: cause?.message ?: errorMessage)
            .withCause(cause)
    ) {
    enum class Code(val status: Status) {
        NOT_FOUND(Status.NOT_FOUND),
        ALREADY_EXISTS(Status.ALREADY_EXISTS)
    }
}

class BadRequestException(
    val code: Code,
    val badRequest: BadRequest,
    cause: Throwable? = null,
    errorMessage: String? = null
) :
    PaceStatusException(
        code.status
            .withDescription(
                cause?.message
                    ?: errorMessage
                    ?: "Violations: ${
                badRequest.fieldViolationsList.joinToString(", ") { it.description }
                    .ifEmpty { "violations missing. $BUG_REPORT" }
            }"
            )
            .withCause(cause)
    ) {
    enum class Code(val status: Status) {
        INVALID_ARGUMENT(Status.INVALID_ARGUMENT),
        OUT_OF_RANGE(Status.OUT_OF_RANGE)
    }
}

class PreconditionFailedException(
    val code: Code,
    val preconditionFailure: PreconditionFailure,
    cause: Throwable? = null,
    errorMessage: String? = null
) :
    PaceStatusException(
        code.status
            .withDescription(
                cause?.message
                    ?: errorMessage
                    ?: "Violations: ${
                preconditionFailure.violationsList.joinToString(", ") { it.description }
                    .ifEmpty { "violations missing. $BUG_REPORT" }
            }"
            )
            .withCause(cause)
    ) {
    enum class Code(val status: Status) {
        FAILED_PRECONDITION(Status.FAILED_PRECONDITION)
    }
}

class ClientErrorException(val code: Code, val errorInfo: ErrorInfo, cause: Throwable? = null) :
    PaceStatusException(
        code.status.withDescription(cause?.message ?: errorInfo.reason).withCause(cause)
    ) {
    enum class Code(val status: Status) {
        UNAUTHENTICATED(Status.UNAUTHENTICATED),
        PERMISSION_DENIED(Status.PERMISSION_DENIED),
        ABORTED(Status.ABORTED)
    }
}

class QuotaFailureException(
    val code: Code,
    val quotaFailure: QuotaFailure,
    cause: Throwable? = null,
    errorMessage: String? = null
) :
    PaceStatusException(
        code.status
            .withDescription(
                cause?.message
                    ?: errorMessage
                    ?: "Violations: ${
                quotaFailure.violationsList.joinToString(", ") { it.description }
                    .ifEmpty { "violations missing. $BUG_REPORT" }
            }"
            )
            .withCause(cause)
    ) {
    enum class Code(val status: Status) {
        RESOURCE_EXHAUSTED(Status.RESOURCE_EXHAUSTED)
    }
}

class InternalException(val code: Code, val debugInfo: DebugInfo, cause: Throwable? = null) :
    PaceStatusException(
        code.status
            .withDescription(
                debugInfo.detail.ifEmpty { null }
                    ?: cause?.message
                    ?: "Error description missing. $BUG_REPORT"
            )
            .withCause(cause)
    ) {
    enum class Code(val status: Status) {
        DATA_LOSS(Status.DATA_LOSS),
        UNKNOWN(Status.UNKNOWN),
        INTERNAL(Status.INTERNAL),
        UNAVAILABLE(Status.UNAVAILABLE),
        DEADLINE_EXCEEDED(Status.DEADLINE_EXCEEDED),
    }
}

fun throwNotFound(id: String, type: String): Nothing {
    throw ResourceException(
        ResourceException.Code.NOT_FOUND,
        ResourceInfo.newBuilder().setResourceName(id).setResourceType(type).build()
    )
}

fun throwUnimplemented(what: String): Nothing {
    throw InternalException(
        InternalException.Code.INTERNAL,
        DebugInfo.newBuilder()
            .setDetail(
                "$what is not implemented yet. Create an issue on https://github.com/getstrm/pace if you need this"
            )
            .build()
    )
}

fun internalExceptionOneOfNotProvided(): InternalException {
    return InternalException(
        InternalException.Code.INTERNAL,
        DebugInfo.newBuilder()
            .setDetail(
                "oneof field not provided, this should not happen as protovalidate catches this."
            )
            .build()
    )
}
