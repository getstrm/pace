import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.cloud.bigquery.Table
import com.google.cloud.bigquery.TableId
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.Message
import com.google.protobuf.MessageOrBuilder
import com.google.protobuf.Timestamp
import com.google.protobuf.util.JsonFormat
import com.google.protobuf.util.Timestamps
import io.strmprivacy.grpc.common.authz.ZedTokenContext
import io.strmprivacy.grpc.common.server.InvalidArgumentException
import io.strmprivacy.grpc.common.server.StrmStatusException
import org.apache.commons.codec.digest.MurmurHash3
import org.jooq.*
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*
import kotlin.coroutines.coroutineContext
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy.RuleSet.FieldTransform.Transform as ApiTransform
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy.RuleSet.FieldTransform as ApiFieldTransform

val log by lazy { LoggerFactory.getLogger("Util") }

suspend fun <R> coUnwrapStatusException(block: suspend () -> R): R {
    try {
        return block()
    } catch (dae: DataAccessException) {
        val strmStatusException = getFirstStrmStatusException(dae)
        if (strmStatusException != null) throw strmStatusException else throw dae
    }
}

private fun getFirstStrmStatusException(throwable: Throwable): StrmStatusException? {
    if (throwable is StrmStatusException) {
        return throwable
    }
    if (throwable.cause != null) {
        return getFirstStrmStatusException(throwable.cause!!)
    }
    return null
}

suspend fun <R> DSLContext.coWithTransactionResult(transactionalBlock: suspend (DSLContext) -> R): R =
    coUnwrapStatusException {
        transactionCoroutine { config ->
            transactionalBlock(DSL.using(config))
        }
    }

suspend fun <R> DSLContext.coWithTransaction(transactionalBlock: suspend (DSLContext) -> R): R =
    coUnwrapStatusException {
        transactionCoroutine { config ->
            transactionalBlock(DSL.using(config))
        }
    }

/**
 * This bridges from the reactive-streams API (mono) to coroutines, while preserving the coroutine context.
 */
@Suppress("UNCHECKED_CAST")
suspend fun <T> DSLContext.transactionCoroutine(transactional: suspend (Configuration) -> T): T {
    val context = checkNotNull(coroutineContext[ZedTokenContext]) {
        "No ZedTokenContext found in coroutine context - should be set by the ZedTokenRequestServerInterceptor"
    }

    return transactional as T
//    return transactionPublisher { c ->
//        mono(context = context) {
//            transactional.invoke(c)
//        }
//    }.awaitFirstOrNull() as T
}

val mapper = jacksonObjectMapper()

/**
 * [JsonFormat] unfortunately doesn't support converting a List<T : [GeneratedMessageV3]> to
 * Json, as Protobuf doesn't support collections of Proto messages at the root.
 * Therefore, we need this workaround where we manually construct a valid JSON.
 * A regular ObjectMapper or Gson would not work here, as the conversion for Proto messages
 * is more complex than a regular POJO.
 *
 * This approach is also used in {@see SerializationSchemasDao}.
 */
fun Collection<MessageOrBuilder>.toJsonArray(jsonPrinter: JsonFormat.Printer): JSONB {
    val json = map { jsonPrinter.print(it) }.toString()
    return JSONB.jsonb(json)
}

fun GeneratedMessageV3.toJsonb(): JSONB {
    return JSONB.valueOf(toJson())
}

fun GeneratedMessageV3.toJson(): String = JsonFormat.printer()
    .omittingInsignificantWhitespace()
    .print(this)

fun GeneratedMessageV3.toJsonWithDefaults(): String = JsonFormat.printer()
    .omittingInsignificantWhitespace()
    .includingDefaultValueFields()
    .print(this)

fun <E> Collection<E>.toJsonb(): JSONB = JSONB.jsonb(mapper.writeValueAsString(this.toSet()))

fun String?.toUUID(idFieldName: String): UUID = this?.let {
    try {
        UUID.fromString(this)
    } catch (e: IllegalArgumentException) {
        throw InvalidArgumentException("The provided ID for $idFieldName '$this' is not a valid UUID.")
    }
} ?: throw InvalidArgumentException("The provided ID for $idFieldName '$this' is null, please provide a valid value.")

fun generateChecksum(message: Message): String {
    val hash = MurmurHash3.hash128x64(message.toByteArray())
    return hash.first().toString()
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

fun GeneratedMessageV3.toYaml(): String =
    ObjectMapper(YAMLFactory()).writeValueAsString(ObjectMapper().readTree(toJsonWithDefaults()))

fun String.parseDataPolicy(): DataPolicy = let {
    val builder = DataPolicy.newBuilder()
    JsonFormat.parser().ignoringUnknownFields().merge(this, builder)
    builder.build()
}

fun String.parseTransforms(): List<ApiTransform> = let {
    val builder = ApiFieldTransform.newBuilder()
    JsonFormat.parser().merge(this.yaml2json(), builder)
    builder.build().transformsList
}

fun Long.toTimestamp(): Timestamp {
    val offsetDateTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
    return Timestamp.newBuilder()
        .setSeconds(offsetDateTime.toEpochSecond())
        .setNanos(offsetDateTime.nano)
        .build()
}

fun DataPolicy.Source.extractFieldPaths(): Set<DataPolicy.Attribute> {
    return when (this.type) {
        DataPolicy.Source.Type.SQL_DDL -> {
            if (attributesCount > 0) return attributesList.toSet()
            TODO("In order to remove JSQL Parser, I've removed the parsing here. Jooq is also capable of doing this, but this function is only used in tests.")
        }

        else -> throw NotImplementedError("Unsupported source type: ${this.type}")
    }
}

fun Timestamp.toOffsetDateTime(): OffsetDateTime = Instant.ofEpochMilli(Timestamps.toMillis(this)).toOffsetDateTime()

fun Instant.toOffsetDateTime() = this.atOffset(ZoneOffset.UTC)

fun OffsetDateTime.toTimestamp() = Timestamp.newBuilder()
    .setSeconds(toEpochSecond())
    .setNanos(nano)
    .build()

fun Table.toFullName() = tableId.toFullName()

fun TableId.toFullName() = "$project.$dataset.$table"

fun DataPolicy.Attribute.pathString() = this.pathComponentsList.joinToString()

fun <T, Accumulator, Result> List<T>.headTailFold(
    headOperation: (T) -> Accumulator,
    bodyOperation: (Accumulator, T) -> Accumulator,
    tailOperation: (Accumulator, T) -> Result,
): Result {
    var accumulator = headOperation(this.first())
    for (element in this.drop(1).dropLast(1)) {
        accumulator = bodyOperation(accumulator, element)
    }
    return tailOperation(accumulator, this.last())
}

fun DataPolicy.Attribute.sqlDataType(): DataType<*> =
    try {
        if(type.lowercase()=="struct") {
            SQLDataType.RECORD
        } else {
            sqlParser.parseField("a::$type").dataType.sqlDataType!!
        }
    } catch (e: Exception) {
        log.warn("Can't parse {}, default to VARCHAR", type)
        SQLDataType.VARCHAR
    }

fun DataPolicy.Attribute.normalizeType(): DataPolicy.Attribute =
    toBuilder().setType( sqlDataType().typeName).build()

val sqlParser = DSL.using(SQLDialect.DEFAULT).parser()
