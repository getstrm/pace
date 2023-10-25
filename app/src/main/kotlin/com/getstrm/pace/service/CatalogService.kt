package com.getstrm.pace.service

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.getstrm.pace.catalogs.CollibraCatalog
import com.getstrm.pace.catalogs.DatahubCatalog
import com.getstrm.pace.catalogs.OpenDataDiscoveryCatalog
import com.getstrm.pace.config.CatalogsConfiguration
import com.getstrm.pace.domain.*
import com.getstrm.pace.exceptions.ResourceException
import com.google.rpc.ResourceInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataCatalog as ApiCatalog
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataCatalog.DataBase as ApiDatabase
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataCatalog.Schema as ApiSchema
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataCatalog.Table as ApiTable

@Component
class CatalogService(
    private val appConfig: CatalogsConfiguration,
) {
    val log by lazy { LoggerFactory.getLogger(javaClass) }

    val catalogs: Map<String, DataCatalog> = appConfig.catalogs.mapNotNull { config ->
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

    suspend fun listDatabases(apiCatalog: ApiCatalog): List<ApiDatabase> =
        getCatalog(apiCatalog.id).listDatabases().map { it.apiDatabase }

    suspend fun listSchemas(apiDatabase: ApiDatabase): List<ApiSchema> {
        val catalog = getCatalog(apiDatabase.catalog.id)
        val database = catalog.listDatabases().find { it.id == apiDatabase.id }
            ?: throw ResourceException(
                ResourceException.Code.NOT_FOUND,
                ResourceInfo.newBuilder()
                    .setResourceType("Database")
                    .setResourceName(apiDatabase.id)
                    .setDescription("Database ${apiDatabase.id} not found in catalog ${apiDatabase.catalog.id}")
                    .setOwner("Catalog: ${apiDatabase.catalog.id}")
                    .build()
            )
        val schemas = database.getSchemas()
        return schemas.map { it.apiSchema }
    }

    suspend fun listTables(apiSchema: ApiSchema): List<ApiTable> =
        getTablesInfo(apiSchema).map { it.apiTable }

    suspend fun getBarePolicy(apiTable: ApiTable): DataPolicy {
        val tables = getTablesInfo(apiTable.schema)
        val table = tables.find { it.id == apiTable.id }
            ?: throw ResourceException(
                ResourceException.Code.NOT_FOUND,
                ResourceInfo.newBuilder()
                    .setResourceType("Table")
                    .setResourceName(apiTable.id)
                    .setDescription("Table ${apiTable.id} not found in schema ${apiTable.schema.id}")
                    .setOwner("Schema: ${apiTable.schema.id}")
                    .build()
            )
        return table.getDataPolicy()!!
    }

    /**
     * find all the tables in an apiSchema.
     *
     * @return dto object with all relevant info
     */
    private suspend fun getTablesInfo(apiSchema: ApiSchema): List<DataCatalog.Table> {
        val catalog = getCatalog(apiSchema.database.catalog.id)
        val database = catalog.listDatabases().find { it.id == apiSchema.database.id }
            ?: throw ResourceException(
                ResourceException.Code.NOT_FOUND,
                ResourceInfo.newBuilder()
                    .setResourceType("Catalog Database")
                    .setResourceName(apiSchema.database.id)
                    .setDescription("Database ${apiSchema.database.id} not found in catalog ${apiSchema.database.catalog.id}")
                    .setOwner("Catalog: ${apiSchema.database.catalog.id}")
                    .build()
            )
        val schema = database.getSchemas().find { it.id == apiSchema.id } ?: throw ResourceException(
            ResourceException.Code.NOT_FOUND,
            ResourceInfo.newBuilder()
                .setResourceType("Catalog Database Schema")
                .setResourceName(apiSchema.id)
                .setDescription("Schema ${apiSchema.id} not found in database ${apiSchema.database.id} of catalog ${apiSchema.database.catalog.id}")
                .setOwner("Database: ${apiSchema.database.id}")
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
