package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.catalogs.CollibraCatalog
import com.getstrm.pace.catalogs.DataCatalog
import com.getstrm.pace.catalogs.DatahubCatalog
import com.getstrm.pace.catalogs.OpenDataDiscoveryCatalog
import com.getstrm.pace.config.CatalogsConfiguration
import com.getstrm.pace.exceptions.ResourceException
import com.google.rpc.ResourceInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog as ApiCatalog
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog.Database as ApiDatabase
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog.Schema as ApiSchema
import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog.Table as ApiTable

@Component
class DataCatalogsService(
    catalogsConfig: CatalogsConfiguration,
) {
    private val log by lazy { LoggerFactory.getLogger(javaClass) }

    val catalogs: Map<String, DataCatalog> = catalogsConfig.catalogs.mapNotNull { config ->
        try {
            when (config.type) {
                ApiCatalog.Type.TYPE_UNSPECIFIED -> null
                ApiCatalog.Type.COLLIBRA -> CollibraCatalog(config)
                ApiCatalog.Type.ODD -> OpenDataDiscoveryCatalog(config)
                ApiCatalog.Type.DATAHUB -> DatahubCatalog(config)
                ApiCatalog.Type.UNRECOGNIZED -> null
            }
        } catch (e: Exception) {
            log.warn("Can't instantiate DataCatalog ${config.id}/${config.type}: {}", e.message)
            null
        }
    }.associateBy { it.id }

    fun listCatalogs(): List<ApiCatalog> = catalogs.map { (id, platform) ->
        ApiCatalog.newBuilder()
            .setId(id)
            .setType(platform.type)
            .build()
    }

    suspend fun listDatabases(catalogId: String): List<ApiDatabase> =
        getCatalog(catalogId).listDatabases().map { it.apiDatabase }

    suspend fun listSchemas(catalogId: String, databaseId: String): List<ApiSchema> {
        val catalog = getCatalog(catalogId)
        val database = catalog.listDatabases().firstOrNull { it.id == databaseId }
            ?: throw ResourceException(
                ResourceException.Code.NOT_FOUND,
                ResourceInfo.newBuilder()
                    .setResourceType("Database")
                    .setResourceName(databaseId)
                    .setDescription("Database $databaseId not found in catalog $catalogId")
                    .setOwner("Catalog: $catalogId")
                    .build()
            )
        val schemas = database.getSchemas()
        return schemas.map { it.apiSchema }
    }

    suspend fun listTables(
        catalogId: String,
        databaseId: String,
        schemaId: String,
    ): List<ApiTable> =
        getTablesInfo(catalogId, databaseId, schemaId).map { it.apiTable }

    suspend fun getBarePolicy(
        catalogId: String,
        databaseId: String,
        schemaId: String,
        tableId: String,
    ): DataPolicy {
        val tables = getTablesInfo(catalogId, databaseId, schemaId)
        val table = tables.firstOrNull { it.id == tableId }
            ?: throw ResourceException(
                ResourceException.Code.NOT_FOUND,
                ResourceInfo.newBuilder()
                    .setResourceType("Table")
                    .setResourceName(tableId)
                    .setDescription("Table $tableId not found in schema $schemaId")
                    .setOwner("Schema: $schemaId")
                    .build()
            )
        // Todo: refactor, get rid of !!
        return table.getDataPolicy()!!
    }

    /**
     * find all the tables in an apiSchema.
     *
     * @return dto object with all relevant info
     */
    private suspend fun getTablesInfo(
        catalogId: String,
        databaseId: String,
        schemaId: String,
    ): List<DataCatalog.Table> {
        val catalog = getCatalog(catalogId)
        val database = catalog.listDatabases().firstOrNull { it.id == databaseId }
            ?: throw ResourceException(
                ResourceException.Code.NOT_FOUND,
                ResourceInfo.newBuilder()
                    .setResourceType("Catalog Database")
                    .setResourceName(databaseId)
                    .setDescription("Database $databaseId not found in catalog $catalogId")
                    .setOwner("Catalog: $catalogId")
                    .build()
            )
        val schema = database.getSchemas().firstOrNull { it.id == schemaId } ?: throw ResourceException(
            ResourceException.Code.NOT_FOUND,
            ResourceInfo.newBuilder()
                .setResourceType("Catalog Database Schema")
                .setResourceName(schemaId)
                .setDescription("Schema $schemaId not found in database $databaseId of catalog $catalogId")
                .setOwner("Database: $databaseId")
                .build()
        )

        return schema.getTables()
    }

    private fun getCatalog(id: String): DataCatalog =
        catalogs[id] ?: throw ResourceException(
            ResourceException.Code.NOT_FOUND,
            ResourceInfo.newBuilder()
                .setResourceType("Catalog")
                .setResourceName(id)
                .setDescription("Catalog $id not found, please ensure it is present in the configuration of the catalogs.")
                .build()
        )
}
