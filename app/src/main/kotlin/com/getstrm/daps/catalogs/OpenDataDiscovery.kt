package com.getstrm.daps.catalogs

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataPolicy
import com.getstrm.daps.config.CatalogConfiguration
import com.getstrm.daps.domain.DataCatalog
import org.opendatadiscovery.generated.api.DataSetApi
import org.opendatadiscovery.generated.api.DataSourceApi
import org.opendatadiscovery.generated.api.SearchApi
import org.opendatadiscovery.generated.model.*
import java.util.*

/**
 * Interface to ODD Data Catalogs. Can be optimized using a cache.
 */
class OpenDataDiscoveryCatalog(configuration: CatalogConfiguration = ODDConfiguration()) : DataCatalog() {
    private val searchClient = SearchApi(configuration.serverUrl)
    private val datasetsClient = DataSetApi(configuration.serverUrl)
    private val dataSourceClient = DataSourceApi(configuration.serverUrl)

    private val dataSources = getAllDataSources().associateBy { it.id }

    override suspend fun listDatabases(): List<Database> {
        val searchRef = searchClient.search(
            SearchFormData(
                query = "",
                filters = SearchFormDataFilters(
                    entityClasses = listOf(
                        SearchFilterState(
                            entityName = "DATA_SET",
                            // selected means that this is included in the search results
                            selected = true,
                            // Not sure why the entity id is necessary, I think it
                            // refers to the entity class id, which "should be" a static
                            // value, in case of the entity type DATA_SET it is 1
                            entityId = 1
                        )
                    )
                )
            )
        )

        return getAllSearchResults(searchRef.searchId).map {
            val dataSource = dataSources[it.dataSource.id]?.name ?: "unknown"
            Database(this, it.id.toString(), dataSource, it.externalName)
        }
    }

    private fun getAllSearchResults(searchId: UUID, page: Int = 1, size: Int = 100): List<DataEntity> {
        val searchResults = searchClient.getSearchResults(searchId, page, size)

        return if (searchResults.items.size == size) {
            searchResults.items + getAllSearchResults(searchId, page + 1, size)
        } else {
            searchResults.items
        }
    }

    private fun getAllDataSources(page: Int = 1, size: Int = 100): List<DataSource> {
        val dataSources = dataSourceClient.getDataSourceList(page, size)

        return if (dataSources.items.size == size) {
            dataSources.items + getAllDataSources(page + 1, size)
        } else {
            dataSources.items
        }
    }

    override fun close() {
    }

    class Database(private val catalog: OpenDataDiscoveryCatalog, id: String, dbType: String, displayName: String) :
        DataCatalog.Database(id, dbType, displayName) {
        /** Effectively a noop, but that's because ODD's data model isn't hierachical
         */
        override suspend fun getSchemas(): List<Schema> {
            return listOf(Schema(catalog, this, id, dbType!!))
        }
    }

    class Schema(
        private val catalog: OpenDataDiscoveryCatalog,
        database: DataCatalog.Database,
        id: String,
        name: String
    ) :
        DataCatalog.Schema(database, id, name) {
        override suspend fun getTables(): List<DataCatalog.Table> {
            return listOf(Table(catalog, this, id, name))
        }
    }

    class Table(private val catalog: OpenDataDiscoveryCatalog, schema: DataCatalog.Schema, id: String, name: String) :
        DataCatalog.Table(schema, id, name) {

        private fun getAllParents(parentId: Long, fieldsById: Map<Long, DataSetField>): List<Long> {
            val parent = fieldsById[parentId]!!

            return if (parent.parentFieldId != null) {
                listOf(parent.id) + getAllParents(parent.parentFieldId, fieldsById)
            } else {
                listOf(parent.id)
            }
        }

        override suspend fun getDataPolicy(): DataPolicy? {
            val datasetStructure = catalog.datasetsClient.getDataSetStructureLatest(schema.database.id.toLong())
            val fields = datasetStructure.fieldList
            val fieldsById = fields.associateBy { it.id }

            val policyBuilder = DataPolicy.newBuilder()

            policyBuilder.sourceBuilder.addAllAttributes(
                fields.map { field ->
                    val fieldPath = if (field.parentFieldId != null) {
                        val parentIds = getAllParents(field.parentFieldId, fieldsById)
                        parentIds.map { fieldsById[it]!!.name }
                    } else {
                        listOf(field.name)
                    }

                    DataPolicy.Attribute.newBuilder()
                        .addAllPathComponents(fieldPath)
                        .setType(field.type.logicalType)
                        .setRequired(!field.type.isNullable)
                        .build()
                }
            )

            policyBuilder.infoBuilder.title = schema.database.displayName
            policyBuilder.infoBuilder.description = schema.database.dbType

            return policyBuilder.build()
        }
    }

    class ODDConfiguration(
        val serverUrl: String = "http://localhost:8080"
    ) : Configuration()
}
