package com.getstrm.pace.util

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageInfo
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import kotlin.math.min
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal val YAML_MAPPER = YAMLMapper()
internal val YAML_MAPPER_NO_DOC_START_MARKER =
    YAMLMapper().apply { configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false) }
internal val JSON_MAPPER = JsonMapper()

fun String.yamlToJson(shouldThrow: Boolean = false): String? {
    return try {
        JSON_MAPPER.writeValueAsString(YAML_MAPPER.readValue(this, Any::class.java))
    } catch (e: JsonProcessingException) {
        if (shouldThrow) throw e else null
    }
}

/**
 * Apply different operations on the head, tail and body of a collection. The head and tail contain
 * a single element, and the body contains the rest of the elements in the collection. Requires at
 * least 2 elements in the collection.
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

private fun DataPolicy.RuleSet.Filter.listPrincipals() =
    when (this.filterCase) {
        DataPolicy.RuleSet.Filter.FilterCase.RETENTION_FILTER ->
            this.retentionFilter.conditionsList.flatMap { it.principalsList }
        DataPolicy.RuleSet.Filter.FilterCase.GENERIC_FILTER ->
            this.genericFilter.conditionsList.flatMap { it.principalsList }
        else -> throw IllegalArgumentException("Unsupported filter: ${this.filterCase.name}")
    }

/**
 * safe page parameters application to a List Can't throw an exception. skip and pageSize are
 * protobuf uint32 so can never be negative.
 */
fun <T> Iterable<T>.applyPageParameters(p: PageParameters): List<T> =
    asSequence().drop(p.skip).take(p.pageSize).toList()

data class PagedCollection<T>(val data: Collection<T>, val pageInfo: PageInfo) {
    fun <V> map(transform: (T) -> V): PagedCollection<V> =
        data.map(transform).withPageInfo(pageInfo)

    suspend fun <V> coMap(transform: suspend (T) -> V): PagedCollection<V> =
        coroutineScope { data.map { async { transform(it) } }.awaitAll() }.withPageInfo(pageInfo)

    fun firstOrNull() = data.firstOrNull()

    fun find(predicate: (T) -> Boolean) = data.find(predicate)

    fun filter(predicate: (T) -> Boolean) = data.filter(predicate).withPageInfo()

    val size = data.size
}

fun <T> Collection<T>.withPageInfo(
    pageInfo: PageInfo =
        // the default is a non-paged collection, so we fill in the total with the size of the
        // collection
        PageInfo.newBuilder().setTotal(size).build()
) = PagedCollection(this, pageInfo)

fun <T> Collection<T>.withPageInfo(nextPageToken: String?) =
    withPageInfo(
        PageInfo.newBuilder().setTotal(-1).setNextPageToken(nextPageToken.orEmpty()).build()
    )

fun <T> Collection<T>.withTotal(total: Int) =
    PagedCollection(this, PageInfo.newBuilder().setTotal(total).build())

fun <T> Collection<T>.withUnknownTotals() = withTotal(-1)

private const val MAX_PAGE_SIZE = 100
private const val DEFAULT_PAGE_SIZE = 10
private const val MAX_PAGE_SIZE_PER_REQUEST = 20

/**
 * retrieve the requested information via 1 or more calls to a backend.
 *
 * @param pageParameters the requested set of data
 * @param queryFunction the function that accesses the backend. skip: the offset when interacting
 *   with the backend. pageSize: the number of entries to request from the backend.
 * @return a List of T with a concatenation of the various call results
 */
suspend fun <T> pagedCalls(
    pageParameters: PageParameters?,
    queryFunction: suspend (skip: Int, pageSize: Int) -> List<T>
): List<T> {
    val assets = ArrayList<T>()
    val totalToRetrieve = min(MAX_PAGE_SIZE, pageParameters?.pageSize.orDefault(DEFAULT_PAGE_SIZE))
    while (assets.size < totalToRetrieve) {
        val remaining = totalToRetrieve - assets.size
        val pageSize = min(remaining, MAX_PAGE_SIZE_PER_REQUEST)
        val pagedAssets = queryFunction((pageParameters?.skip ?: 0) + assets.size, pageSize)
        if (pagedAssets.isEmpty()) {
            break
        }
        assets += pagedAssets
    }
    return assets
}

fun <T> List<T?>?.onlyNonNulls(): List<T> = orEmpty().filterNotNull()

fun <T> List<T?>?.firstNonNull(): T = onlyNonNulls().first()

/** replace 0 or null with default */
fun <T> T?.orDefault(default: T): T = if (this == 0 || this == null) default else this
