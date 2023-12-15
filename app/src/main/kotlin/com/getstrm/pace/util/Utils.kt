package com.getstrm.pace.util

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageInfo
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.google.cloud.bigquery.Table
import com.google.cloud.bigquery.TableId
import org.jooq.*
import java.util.stream.Collectors
import kotlin.math.max

internal val YAML_MAPPER = ObjectMapper(YAMLFactory())
internal val JSON_MAPPER = ObjectMapper()

fun String.yamlToJson(shouldThrow: Boolean = false): String? {
    return try {
        JSON_MAPPER.writeValueAsString(YAML_MAPPER.readValue(this, Any::class.java))
    } catch (e: JsonProcessingException) {
        if (shouldThrow) throw e else null
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

/**
 * safe page parameters application.
 * Can't throw an exception
 */
fun <T>List<T>.applyPageParameters(p:PageParameters) : List<T> =
        stream()
            .skip(max(p.skip, 0).toLong())
            .limit(max(p.pageSize,0).toLong())
            .collect(Collectors.toList())


data class PagedCollection<T>(val data: Collection<T>, val pageInfo: PageInfo){
    fun <V> map(transform: (T) -> V): PagedCollection<V> = data.map(transform).withPageInfo(pageInfo)
}

fun <T> Collection<T>.withPageInfo(pageInfo: PageInfo =
    // the default is a non-paged collection, so we fill in the total with the size of the collection
                                       PageInfo.newBuilder().setTotal(size).build()) = PagedCollection(this, pageInfo)

fun <T> Collection<T>.withTotal(total: Int) = PagedCollection(this, PageInfo.newBuilder().setTotal(total).build())
fun <T> Collection<T>.withUnknownTotals() = withTotal(-1)
