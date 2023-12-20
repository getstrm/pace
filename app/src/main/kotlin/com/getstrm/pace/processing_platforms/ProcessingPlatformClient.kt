package com.getstrm.pace.processing_platforms

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.exceptions.ResourceException
import com.google.rpc.ResourceInfo
import org.jooq.Field

interface ProcessingPlatformClient {
    val id: String
    val type: DataPolicy.ProcessingPlatform.PlatformType

    suspend fun listGroups(): List<Group>

    suspend fun listTables(): List<Table>

    suspend fun applyPolicy(dataPolicy: DataPolicy)

    suspend fun getTable(tableName: String): Table =
        listTables().find { it.fullName == tableName }
            ?: throw ResourceException(
                ResourceException.Code.NOT_FOUND,
                ResourceInfo.newBuilder()
                    .setResourceType("Table")
                    .setResourceName(tableName)
                    .setDescription("Table $tableName not found in platform $id of type $type")
                    .setOwner("Processing Platform: ${type.name}")
                    .build()
            )
}

data class Group(val id: String, val name: String, val description: String? = null)

abstract class Table {
    abstract val fullName: String

    abstract suspend fun toDataPolicy(platform: DataPolicy.ProcessingPlatform): DataPolicy

    override fun toString(): String = "${javaClass.simpleName}(fullName=$fullName)"

    companion object {
        private val regex = """(?:pace)\:\:((?:\"[\w\s\-\_]+\"|[\w\-\_]+))""".toRegex()

        fun <T> Field<T>.toTags(): List<String> {
            val match = regex.findAll(comment)
            return match.map { it.groupValues[1].trim('"') }.toList()
        }
    }
}
