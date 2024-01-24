package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.data_catalogs.v1alpha.*
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog as ApiCatalog
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Database as ApiDatabase
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Schema as ApiSchema
import build.buf.gen.getstrm.pace.api.entities.v1alpha.Table as ApiTable
import com.getstrm.pace.catalogs.CollibraCatalog
import com.getstrm.pace.catalogs.DataCatalog
import com.getstrm.pace.catalogs.DatahubCatalog
import com.getstrm.pace.catalogs.OpenDataDiscoveryCatalog
import com.getstrm.pace.config.AppConfiguration
import com.getstrm.pace.exceptions.InternalException
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.orDefault
import com.google.rpc.DebugInfo
import com.google.rpc.ResourceInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DataCatalogsService(
    catalogsConfig: AppConfiguration,
    val globalTransformsService: GlobalTransformsService,
) {
    private val log by lazy { LoggerFactory.getLogger(javaClass) }

    val catalogs: Map<String, DataCatalog> =
        catalogsConfig.catalogs
            .mapNotNull { config ->
                try {
                    when (config.type) {
                        ApiCatalog.Type.TYPE_UNSPECIFIED -> null
                        ApiCatalog.Type.COLLIBRA -> CollibraCatalog(config)
                        ApiCatalog.Type.ODD -> OpenDataDiscoveryCatalog(config)
                        ApiCatalog.Type.DATAHUB -> DatahubCatalog(config)
                        ApiCatalog.Type.UNRECOGNIZED -> null
                    }
                } catch (e: Exception) {
                    log.warn(
                        "Can't instantiate DataCatalog ${config.id}/${config.type}: {}",
                        e.message
                    )
                    null
                }
            }
            .associateBy { it.id }

    // Ignoring paging for now
    fun listCatalogs(request: ListCatalogsRequest): List<ApiCatalog> =
        catalogs.map { (id, platform) ->
            ApiCatalog.newBuilder().setId(id).setType(platform.type).build()
        }

    suspend fun listDatabases(request: ListDatabasesRequest): PagedCollection<ApiDatabase> =
        with(request) {
            val databases = getCatalog(catalogId).listDatabases(pageParameters.orDefault())
            databases.map { it.apiDatabase }
        }

    suspend fun listSchemas(request: ListSchemasRequest): PagedCollection<ApiSchema> =
        with(request) {
            val schemas = getCatalog(catalogId).getDatabase(databaseId).listSchemas()
            return@with schemas.map { it.apiSchema }
        }

    suspend fun listTables(request: ListTablesRequest): PagedCollection<ApiTable> =
        with(request) {
            val catalog = getCatalog(catalogId)
            val database = catalog.getDatabase(databaseId)
            val schema = database.getSchema(schemaId)
            schema.listTables(request.pageParameters.orDefault()).map { it.apiTable }
        }

    suspend fun getBlueprintPolicy(
        catalogId: String,
        databaseId: String,
        schemaId: String,
        tableId: String,
    ): DataPolicy {
        val schema = getCatalog(catalogId).getDatabase(databaseId).getSchema(schemaId)
        val table = schema.getTable(tableId)
        val blueprint = table.createBlueprint()!!
        val bluePrintWithRulesets =
            try {
                globalTransformsService.addRuleSet(blueprint)
            } catch (e: Exception) {
                log.warn("could not apply global-transforms to this blueprint", e)
                throw InternalException(
                    InternalException.Code.INTERNAL,
                    DebugInfo.newBuilder()
                        .setDetail("could not apply global-transforms to this blueprint")
                        .build()
                )
            }
        return bluePrintWithRulesets
    }

    private fun getCatalog(id: String): DataCatalog =
        catalogs[id]
            ?: throw ResourceException(
                ResourceException.Code.NOT_FOUND,
                ResourceInfo.newBuilder()
                    .setResourceType("Catalog")
                    .setResourceName(id)
                    .setDescription(
                        "Catalog $id not found, please ensure it is present in the configuration of the catalogs."
                    )
                    .build()
            )
}
