package com.getstrm.daps.common

import io.grpc.Status
import io.strmprivacy.grpc.common.server.StrmStatusException
import org.jooq.impl.ParserException

class SqlParseException(statement: String, cause: ParserException) : StrmStatusException(
    Status.INVALID_ARGUMENT,
    "SQL Statement [$statement] is invalid, please verify it's syntax. Details: ${cause.sql()}"
)
