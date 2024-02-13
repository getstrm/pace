package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.Database
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Schema
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Table
import build.buf.gen.getstrm.pace.api.entities.v1alpha.database
import build.buf.gen.getstrm.pace.api.entities.v1alpha.schema
import build.buf.gen.getstrm.pace.api.entities.v1alpha.table
import build.buf.gen.getstrm.pace.api.resources.v1alpha.listResourcesRequest
import com.getstrm.pace.util.PagedCollection
import org.springframework.stereotype.Component

@Component
class LegacyHierarchyService(private val resourcesService: ResourcesService) {

    suspend fun listDatabases(integrationId: String): PagedCollection<Database> {
        val resourcesRequest = listResourcesRequest { this.integrationId = integrationId }
        return resourcesService.listResources(resourcesRequest).map {
            val databaseNode = it.resourcePathList[0]
            database {
                id = databaseNode.name
                displayName = databaseNode.displayName
            }
        }
    }

    suspend fun listSchemas(integrationId: String, databaseId: String): PagedCollection<Schema> {
        val resourcesRequest = listResourcesRequest {
            this.integrationId = integrationId
            this.resourcePath += databaseId
        }

        return resourcesService.listResources(resourcesRequest).map {
            val databaseNode = it.resourcePathList[0]
            val schemaNode = it.resourcePathList[1]
            schema {
                id = schemaNode.name
                name = schemaNode.displayName
                database = database {
                    id = databaseNode.name
                    displayName = databaseNode.displayName
                }
            }
        }
    }

    suspend fun listTables(
        integrationId: String,
        databaseId: String,
        schemaId: String
    ): PagedCollection<Table> {
        val resourcesRequest = listResourcesRequest {
            this.integrationId = integrationId
            this.resourcePath += databaseId
            this.resourcePath += schemaId
        }
        return resourcesService.listResources(resourcesRequest).map {
            val databaseNode = it.resourcePathList[0]
            val schemaNode = it.resourcePathList[1]
            val tableNode = it.resourcePathList[2]
            table {
                id = tableNode.name
                name = tableNode.displayName
                schema = schema {
                    id = schemaNode.name
                    name = schemaNode.displayName
                    database = database {
                        id = databaseNode.name
                        displayName = databaseNode.displayName
                    }
                }
            }
        }
    }
}
