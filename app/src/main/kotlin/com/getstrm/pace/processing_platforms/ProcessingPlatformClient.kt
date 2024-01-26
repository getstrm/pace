package com.getstrm.pace.processing_platforms

import build.buf.gen.getstrm.pace.api.entities.v1alpha.*
import build.buf.gen.getstrm.pace.api.processing_platforms.v1alpha.GetLineageRequest
import com.getstrm.pace.config.ProcessingPlatformConfiguration
import com.getstrm.pace.domain.IntegrationClient
import com.getstrm.pace.exceptions.throwUnimplemented
import org.jooq.Field

abstract class ProcessingPlatformClient(open val config: ProcessingPlatformConfiguration) :
    IntegrationClient() {
    override val id
        get() = config.id

    override val resourceUrn: ResourceUrn
        get() = resourceUrn { platform = apiProcessingPlatform }

    val type: ProcessingPlatform.PlatformType
        get() = config.type

    val apiProcessingPlatform: ProcessingPlatform
        get() = ProcessingPlatform.newBuilder().setId(id).setPlatformType(config.type).build()

    abstract suspend fun applyPolicy(dataPolicy: DataPolicy)

    /** return the up- and downstream tables of a table identified by its fully qualified name. */
    open suspend fun getLineage(request: GetLineageRequest): LineageSummary {
        throwUnimplemented("Lineage in platform ${config.type}")
    }

    companion object {
        private val regex = """(?:pace)\:\:((?:\"[\w\s\-\_]+\"|[\w\-\_]+))""".toRegex()

        /** Used to create Pace tags from a SQL Comment on a jooq Field. */
        fun <T> Field<T>.toTags(): List<String> {
            val match = regex.findAll(comment)
            return match.map { it.groupValues[1].trim('"') }.toList()
        }
    }
}

data class Group(val id: String, val name: String, val description: String? = null)
