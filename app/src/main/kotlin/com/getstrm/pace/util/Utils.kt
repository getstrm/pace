package com.getstrm.pace.util

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.getstrm.pace.exceptions.PaceStatusException
import com.google.cloud.bigquery.Table
import com.google.cloud.bigquery.TableId
import org.jooq.*
import org.jooq.exception.DataAccessException


suspend fun <R> coUnwrapStatusException(block: suspend () -> R): R {
    try {
        return block()
    } catch (dae: DataAccessException) {
        val strmStatusException = getFirstPaceStatusException(dae)
        if (strmStatusException != null) throw strmStatusException else throw dae
    }
}

private fun getFirstPaceStatusException(throwable: Throwable): PaceStatusException? {
    if (throwable is PaceStatusException) {
        return throwable
    }
    return throwable.cause?.let { getFirstPaceStatusException(it) }
}


fun String.yaml2json(): String {
    val yamlReader = ObjectMapper(YAMLFactory())
    return try {
        val obj = yamlReader.readValue(this, Any::class.java)
        val jsonWriter = ObjectMapper()
        jsonWriter.writeValueAsString(obj)
    } catch (e: JsonProcessingException) {
        throw RuntimeException(e)
    }
}

fun Table.toFullName() = tableId.toFullName()

fun TableId.toFullName() = "$project.$dataset.$table"

/**
 * Apply different operations on the head, tail and body of a collection. The head and tail contain a single element,
 * and the body contains the rest of the elements in the collection. Requires at least 2 elements in the collection.
 *
 * @param headOperation Operation to apply on the first element
 * @param bodyOperation Operation to apply on all elements except the first and last
 * @param tailOperation Operation to apply on the last element
 */
fun <T, Accumulator, Result> List<T>.headTailFold(
    headOperation: (T) -> Accumulator,
    bodyOperation: (Accumulator, T) -> Accumulator,
    tailOperation: (Accumulator, T) -> Result,
): Result {
    require(this.size >= 2) { "List must have at least 2 elements" }
    var accumulator = headOperation(this.first())
    for (element in this.drop(1).dropLast(1)) {
        accumulator = bodyOperation(accumulator, element)
    }
    return tailOperation(accumulator, this.last())
}

fun DataPolicy.RuleSet.uniquePrincipals(): Set<DataPolicy.Principal> =
    fieldTransformsList.flatMap { it.transformsList }.flatMap { it.principalsList }.toSet() +
        filtersList.flatMap { it.listPrincipals() }.toSet()

private fun DataPolicy.RuleSet.Filter.listPrincipals() = when (this.filterCase) {
    DataPolicy.RuleSet.Filter.FilterCase.RETENTION_FILTER -> this.retentionFilter.conditionsList.flatMap { it.principalsList }
    DataPolicy.RuleSet.Filter.FilterCase.GENERIC_FILTER -> this.genericFilter.conditionsList.flatMap { it.principalsList }
    else -> throw IllegalArgumentException("Unsupported filter: ${this.filterCase.name}")
}
