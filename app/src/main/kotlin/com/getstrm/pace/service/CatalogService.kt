package com.getstrm.pace.service
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.getstrm.pace.catalogs.CollibraCatalog
import com.getstrm.pace.catalogs.DatahubCatalog
import com.getstrm.pace.catalogs.OpenDataDiscoveryCatalog
import com.getstrm.pace.config.CatalogsConfiguration
import com.getstrm.pace.domain.*
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
            ?: throw CatalogDatabaseNotFoundException(apiDatabase.id)
        val schemas = database.getSchemas()
        return schemas.map { it.apiSchema }
    }

    suspend fun listTables(apiSchema: ApiSchema): List<ApiTable> =
        getTablesInfo(apiSchema).map { it.apiTable }

    suspend fun getBarePolicy(apiTable: ApiTable): DataPolicy {
        val tables = getTablesInfo(apiTable.schema)
        val table = tables.find { it.id == apiTable.id }
            ?: throw CatalogTableNotFoundException(apiTable.id)
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
            ?: throw CatalogDatabaseNotFoundException(apiSchema.database.id)
        val schema =
            database.getSchemas().find { it.id == apiSchema.id } ?: throw CatalogSchemaNotFoundException(apiSchema.id)
        return schema.getTables()
    }

    private fun getCatalog(id: String): DataCatalog =
        catalogs[id] ?: throw CatalogNotFoundException(id)
}
