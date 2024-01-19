package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.Database
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceUrn
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Schema
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Table
import build.buf.gen.getstrm.pace.api.entities.v1alpha.copy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.database
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceNode
import build.buf.gen.getstrm.pace.api.entities.v1alpha.schema
import build.buf.gen.getstrm.pace.api.entities.v1alpha.table
import build.buf.gen.getstrm.pace.api.resources.v1alpha.listResourcesRequest
import com.getstrm.pace.util.PagedCollection
import org.springframework.stereotype.Component

@Component
class LegacyHierarchyService(private val resourcesService: ResourcesService) {

    suspend fun listDatabases(resourceUrn: ResourceUrn): PagedCollection<Database> =
        resourcesService.listResources(listResourcesRequest { urn = resourceUrn }).map {
            val databaseNode = it.resourcePathList[0]
            database {
                id = databaseNode.name
                displayName = databaseNode.displayName
            }
        }

    suspend fun listSchemas(resourceUrn: ResourceUrn, databaseId: String): PagedCollection<Schema> {
        val resourcesRequest = listResourcesRequest {
            urn = resourceUrn.copy { resourcePath.add(resourceNode { name = databaseId }) }
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
        resourceUrn: ResourceUrn,
        databaseId: String,
        schemaId: String
    ): PagedCollection<Table> {
        val resourcesRequest = listResourcesRequest {
            urn =
                resourceUrn.copy {
                    resourcePath.addAll(
                        listOf(resourceNode { name = databaseId }, resourceNode { name = schemaId })
                    )
                }
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
