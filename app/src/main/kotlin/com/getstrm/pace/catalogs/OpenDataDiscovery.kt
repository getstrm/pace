package com.getstrm.pace.catalogs

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.getstrm.pace.config.CatalogConfiguration
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.THOUSAND_RECORDS
import com.getstrm.pace.util.normalizeType
import com.getstrm.pace.util.withPageInfo
import com.google.rpc.ResourceInfo
import org.opendatadiscovery.generated.api.DataEntityApi
import org.opendatadiscovery.generated.api.DataSetApi
import org.opendatadiscovery.generated.api.DataSourceApi
import org.opendatadiscovery.generated.api.SearchApi
import org.opendatadiscovery.generated.model.*
import java.util.*

/**
 * Interface to ODD Data Catalogs.
 */
class OpenDataDiscoveryCatalog(configuration: CatalogConfiguration) : DataCatalog(configuration) {
    private val searchClient = SearchApi(configuration.serverUrl)
    private val datasetsClient = DataSetApi(configuration.serverUrl)
    private val dataEntityClient = DataEntityApi(configuration.serverUrl)
    private val dataSourceClient = DataSourceApi(configuration.serverUrl)
    // FIXME this will only get all datasource once, during Pace startup
    private val dataSources = getAllDataSources().associateBy { it.id }

    /**
     * We're doing a weird workaround, where we search all dataSources, and then select those
     * that contain at least one dataset. This is because ODD does not provide information
     * of this kind in its api.
     */
    override suspend fun listDatabases(pageParameters: PageParameters): PagedCollection<DataCatalog.Database> =
        dataSources.values.filter { dataSource ->
            val searchId = searchDataSetsInDataSource(dataSource).searchId
            // we try for one search result on the first page, we only want to know if there are any.
            searchClient.getSearchResults(searchId, 1, 1).items.isNotEmpty()
        }.map {
            Database(this, it, it.id.toString(), it.namespace?.name ?: "", it.name)
        }.withPageInfo()

    // FIXME the ODD model seems broken. See comment with listDatabases.
    override suspend fun getDatabase(databaseId: String): DataCatalog.Database {
//        val r = dataEntityClient.getDataEntityDetails(databaseId.toLong())
        
        return listDatabases(THOUSAND_RECORDS).data.find{it.id == databaseId}?:
        throw ResourceException(
            ResourceException.Code.NOT_FOUND,
            ResourceInfo.newBuilder()
                .setResourceType("Database")
                .setResourceName(databaseId)
                .setDescription("Database $databaseId not found in catalog $id")
                .setOwner("Catalog: $id")
                .build()


        )
    }

    class Database(
        override val catalog: OpenDataDiscoveryCatalog,
        val dataSource: DataSource,
        id: String,
        dbType: String,
        displayName: String
    ) : DataCatalog.Database(catalog, id, dbType, displayName) {
        /*
            Just returning a hardcoded schema with id 'schema' and name the same as that of the dataSource.
        */
        override suspend fun listSchemas(pageParameters: PageParameters): PagedCollection<DataCatalog.Schema> {
            return listOf(Schema(catalog, this, "schema", dataSource.name)).withPageInfo()
        }

        override suspend fun getSchema(schemaId: String): DataCatalog.Schema {
            return listSchemas(THOUSAND_RECORDS).data.firstOrNull { it.id == schemaId } ?: throw ResourceException(
                ResourceException.Code.NOT_FOUND,
                ResourceInfo.newBuilder()
                    .setResourceType("Catalog Database Schema")
                    .setResourceName(schemaId)
                    .setDescription("Schema $schemaId not found in database $id of catalog $catalog.id")
                    .setOwner("Database: $id")
                    .build()
            )
        }
    }

    class Schema(
        private val catalog: OpenDataDiscoveryCatalog,
        // oddDatabase as a separate value, because we need to access its dataSource.
        private val oddDatabase: Database,
        id: String,
        name: String,
    ) : DataCatalog.Schema(oddDatabase, id, name) {
        override suspend fun listTables(pageParameters: PageParameters): PagedCollection<DataCatalog.Table> {
            val searchId = catalog.searchDataSetsInDataSource(oddDatabase.dataSource).searchId
            val tables = catalog.getAllSearchResults(searchId).map { Table(catalog, this, "${it.id}", it.externalName) }
            return tables.withPageInfo()
        }

        override suspend fun getTable(tableId: String): DataCatalog.Table {
            return listTables(THOUSAND_RECORDS).data.firstOrNull { it.id == tableId }
                ?: throw ResourceException(
                    ResourceException.Code.NOT_FOUND,
                    ResourceInfo.newBuilder()
                        .setResourceType("Table")
                        .setResourceName(tableId)
                        .setDescription("Table $tableId not found in schema $id")
                        .setOwner("Schema: $id")
                        .build()
                )
            
        }
    }

    class Table(
        private val catalog: OpenDataDiscoveryCatalog,
        schema: DataCatalog.Schema,
        id: String,
        name: String
    ) :
        DataCatalog.Table(schema, id, name) {
        private fun getAllParents(parentId: Long, fieldsById: Map<Long, DataSetField>): List<Long> {
            val parent = checkNotNull(fieldsById[parentId]) { "The parent field should exist" }

            return if (parent.parentFieldId != null) {
                listOf(parent.id) + getAllParents(parent.parentFieldId, fieldsById)
            } else {
                listOf(parent.id)
            }
        }

        override suspend fun createBlueprint(): DataPolicy? {
            val datasetStructure = catalog.datasetsClient.getDataSetStructureLatest(id.toLong())
            val fields = datasetStructure.fieldList
            val fieldsById = fields.associateBy { it.id }

            val policyBuilder = DataPolicy.newBuilder()

            policyBuilder.sourceBuilder.addAllFields(
                fields.map { field ->
                    val fieldPath = if (field.parentFieldId != null) {
                        val parentIds = getAllParents(field.parentFieldId, fieldsById)
                        parentIds.map { fieldsById[it]?.name.orEmpty() }
                    } else {
                        listOf(field.name)
                    }

                    DataPolicy.Field.newBuilder()
                        .addAllNameParts(fieldPath)
                        .setType(field.type.logicalType)
                        .setRequired(!field.type.isNullable)
                        .addAllTags(field.tags?.map { it.name }.orEmpty())
                        .build().normalizeType()
                },
            )
            with(policyBuilder.metadataBuilder) {
                title = name
                description = schema.database.displayName
            }
            return policyBuilder.build()
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


    /**
     * search all the datasets in one particular dataSource.
     */
    private fun searchDataSetsInDataSource(dataSource: DataSource): SearchFacetsData =
        searchClient.search(
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
                            entityId = 1,
                        ),
                    ),
                    datasources = listOf(
                        SearchFilterState(
                            entityName = dataSource.name,
                            entityId = dataSource.id,
                            selected = true,
                        )
                    )
                ),
            ),
        )

    /**
     * recursively get all results for a given search id.
     * TODO handle paging.
     */
    private fun getAllSearchResults(searchId: UUID, page: Int = 1, size: Int = 100): List<DataEntity> {
        val searchResults = searchClient.getSearchResults(searchId, page, size)

        return if (searchResults.items.size == size) {
            searchResults.items + getAllSearchResults(searchId, page + 1, size)
        } else {
            searchResults.items
        }
    }
}
