package com.getstrm.pace.exceptions

import com.google.rpc.*
import io.grpc.Status

/**
 * Base class for all exceptions that should be caught by the ExceptionHandlerInterceptor.
 * This construct follows Google API design patterns.
 * See here for more mappings: https://cloud.google.com/apis/design/errors#error_payloads
 */
sealed class PaceStatusException(val status: Status) : Exception(status.cause) {
    companion object {
        const val BUG_REPORT = "This is a bug, please report it to https://github.com/getstrm/pace/issues/new"
        const val UNIMPLEMENTED = "This is an unimplemented feature, please check whether a feature request is present or create a feature request: https://github.com/getstrm/pace/issues"
    }
}

class ResourceException(
    val code: Code,
    val resourceInfo: ResourceInfo,
    cause: Throwable? = null,
    errorMessage: String? = null
) : PaceStatusException(
    code.status
        .withDescription(resourceInfo.description ?: cause?.message ?: errorMessage)
        .withCause(cause)
) {
    enum class Code(val status: Status) {
        NOT_FOUND(Status.NOT_FOUND),
        ALREADY_EXISTS(Status.ALREADY_EXISTS);
    }
}

class BadRequestException(
    val code: Code,
    val badRequest: BadRequest,
    cause: Throwable? = null,
    errorMessage: String? = null
) : PaceStatusException(
    code.status
        .withDescription(
            cause?.message ?: errorMessage
            ?: "Violations: ${
                badRequest.fieldViolationsList.joinToString(", ") { it.description }
                    .ifEmpty { "violations missing. $BUG_REPORT" }
            }"
        )
        .withCause(cause)
) {
    enum class Code(val status: Status) {
        INVALID_ARGUMENT(Status.INVALID_ARGUMENT),
        OUT_OF_RANGE(Status.OUT_OF_RANGE);
    }
}

class PreconditionFailedException(
    val code: Code,
    val preconditionFailure: PreconditionFailure,
    cause: Throwable? = null,
    errorMessage: String? = null
) : PaceStatusException(
    code.status
        .withDescription(
            cause?.message ?: errorMessage
            ?: "Violations: ${
                preconditionFailure.violationsList.joinToString(", ") { it.description }
                    .ifEmpty { "violations missing. $BUG_REPORT" }
            }"
        )
        .withCause(cause)

) {
    enum class Code(val status: Status) {
        FAILED_PRECONDITION(Status.FAILED_PRECONDITION);
    }
}

class ClientErrorException(
    val code: Code,
    val errorInfo: ErrorInfo,
    cause: Throwable? = null
) : PaceStatusException(
    code.status
        .withDescription(cause?.message ?: errorInfo.reason)
        .withCause(cause)
) {
    enum class Code(val status: Status) {
        UNAUTHENTICATED(Status.UNAUTHENTICATED),
        PERMISSION_DENIED(Status.PERMISSION_DENIED),
        ABORTED(Status.ABORTED);
    }
}

class QuotaFailureException(
    val code: Code,
    val quotaFailure: QuotaFailure,
    cause: Throwable? = null,
    errorMessage: String? = null
) : PaceStatusException(
    code.status
        .withDescription(
            cause?.message ?: errorMessage
            ?: "Violations: ${
                quotaFailure.violationsList.joinToString(", ") { it.description }
                    .ifEmpty { "violations missing. $BUG_REPORT" }
            }"
        )
        .withCause(cause)
) {
    enum class Code(val status: Status) {
        RESOURCE_EXHAUSTED(Status.RESOURCE_EXHAUSTED);
    }
}

class InternalException(
    val code: Code,
    val debugInfo: DebugInfo,
    cause: Throwable? = null
) : PaceStatusException(
    code.status
        .withDescription(
            cause?.message ?: debugInfo.detail ?: "Error description missing. $BUG_REPORT"
        )
        .withCause(cause)
) {
    enum class Code(val status: Status) {
        DATA_LOSS(Status.DATA_LOSS),
        UNKNOWN(Status.UNKNOWN),
        INTERNAL(Status.INTERNAL),
        UNAVAILABLE(Status.UNAVAILABLE),
        DEADLINE_EXCEEDED(Status.DEADLINE_EXCEEDED);
    }
}
