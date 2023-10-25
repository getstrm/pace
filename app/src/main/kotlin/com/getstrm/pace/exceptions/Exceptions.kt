package com.getstrm.pace.exceptions

import com.google.rpc.BadRequest
import com.google.rpc.DebugInfo
import com.google.rpc.ErrorInfo
import com.google.rpc.PreconditionFailure
import com.google.rpc.QuotaFailure
import com.google.rpc.ResourceInfo
import io.grpc.Status

/**
 * Base class for all exceptions that should be caught by the ExceptionHandlerInterceptor.
 * This construct follows Google API design patterns.
 * See here for more mappings: https://cloud.google.com/apis/design/errors#error_payloads
 */
sealed class PaceStatusException(val status: Status) : Exception()

class ResourceException(val code: Code, val resourceInfo: ResourceInfo) : PaceStatusException(code.status) {
    enum class Code(val status: Status) {
        NOT_FOUND(Status.NOT_FOUND),
        ALREADY_EXISTS(Status.ALREADY_EXISTS);
    }
}

class BadRequestException(val code: Code, val badRequest: BadRequest) : PaceStatusException(code.status) {
    enum class Code(val status: Status) {
        INVALID_ARGUMENT(Status.INVALID_ARGUMENT),
        OUT_OF_RANGE(Status.OUT_OF_RANGE);
    }
}

class PreconditionFailedException(val code: Code, val preconditionFailure: PreconditionFailure) : PaceStatusException(code.status) {
    enum class Code(val status: Status) {
        FAILED_PRECONDITION(Status.FAILED_PRECONDITION);
    }
}

class ClientErrorException(val code: Code, val errorInfo: ErrorInfo) : PaceStatusException(code.status) {
    enum class Code(val status: Status) {
        UNAUTHENTICATED(Status.UNAUTHENTICATED),
        PERMISSION_DENIED(Status.PERMISSION_DENIED),
        ABORTED(Status.ABORTED);
    }
}

class QuotaFailureException(val code: Code, val quotaFailure: QuotaFailure) : PaceStatusException(code.status) {
    enum class Code(val status: Status) {
        RESOURCE_EXHAUSTED(Status.RESOURCE_EXHAUSTED);
    }
}

class InternalException(val code: Code, val debugInfo: DebugInfo) : PaceStatusException(code.status) {
    enum class Code(val status: Status) {
        DATA_LOSS(Status.DATA_LOSS),
        UNKNOWN(Status.UNKNOWN),
        INTERNAL(Status.INTERNAL),
        UNAVAILABLE(Status.UNAVAILABLE),
        DEADLINE_EXCEEDED(Status.DEADLINE_EXCEEDED);
    }
}
