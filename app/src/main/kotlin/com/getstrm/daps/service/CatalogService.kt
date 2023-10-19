package com.getstrm.daps.service
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataCatalog as ApiCatalog
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataCatalog.Type.COLLIBRA
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataCatalog.Type.DATAHUB
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataCatalog.Type.ODD
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataCatalog.Type.TYPE_UNSPECIFIED
import build.buf.gen.getstrm.api.data_policies.v1alpha.DataCatalog.Type.UNRECOGNIZED
import com.getstrm.daps.catalogs.CollibraCatalog
import com.getstrm.daps.catalogs.DatahubCatalog
import com.getstrm.daps.catalogs.OpenDataDiscoveryCatalog
import com.getstrm.daps.config.CatalogsConfiguration
import com.getstrm.daps.domain.DataCatalog
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CatalogService(
    config: CatalogsConfiguration,
) {
    val log by lazy { LoggerFactory.getLogger(javaClass) }

    val catalogs: Map<String, DataCatalog> = config.catalogs.mapNotNull { config ->
        try {
            when (config.type) {
                TYPE_UNSPECIFIED -> null
                COLLIBRA -> CollibraCatalog(config)
                ODD -> OpenDataDiscoveryCatalog(config)
                DATAHUB -> DatahubCatalog(config)
                UNRECOGNIZED -> null
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
