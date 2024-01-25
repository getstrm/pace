package com.getstrm.pace.catalogs

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import build.buf.gen.getstrm.pace.api.paging.v1alpha.PageParameters
import com.getstrm.pace.config.CatalogConfiguration
import com.getstrm.pace.domain.LeafResource
import com.getstrm.pace.domain.Resource
import com.getstrm.pace.exceptions.ResourceException
import com.getstrm.pace.util.PagedCollection
import com.getstrm.pace.util.THOUSAND_RECORDS
import com.getstrm.pace.util.normalizeType
import com.getstrm.pace.util.withPageInfo
import com.google.rpc.ResourceInfo
import java.util.*
import org.opendatadiscovery.generated.api.DataSetApi
import org.opendatadiscovery.generated.api.DataSourceApi
import org.opendatadiscovery.generated.api.SearchApi
import org.opendatadiscovery.generated.model.DataEntity
import org.opendatadiscovery.generated.model.DataSetField
import org.opendatadiscovery.generated.model.DataSource
import org.opendatadiscovery.generated.model.SearchFacetsData
import org.opendatadiscovery.generated.model.SearchFilterState
import org.opendatadiscovery.generated.model.SearchFormData
import org.opendatadiscovery.generated.model.SearchFormDataFilters

/** Interface to ODD Data Catalogs. */
class OpenDataDiscoveryCatalog(configuration: CatalogConfiguration) : DataCatalog(configuration) {
    private val searchClient = SearchApi(configuration.serverUrl)
    private val datasetsClient = DataSetApi(configuration.serverUrl)
    private val dataSourceClient = DataSourceApi(configuration.serverUrl)

    // FIXME this will only get all datasources once, during Pace startup
    private val dataSources = getAllDataSources().associateBy { it.id }

    override suspend fun getChild(childId: String): Resource = getDatabase(childId)

    /**
     * We're doing a weird workaround, where we search all dataSources, and then select those that
     * contain at least one dataset. This is because ODD does not provide information of this kind
     * in its api.
     */
    override suspend fun listChildren(pageParameters: PageParameters): PagedCollection<Resource> =
        dataSources.values
            .filter { dataSource ->
                val searchId = searchDataSetsInDataSource(dataSource).searchId
                // we try for one search result on the first page, we only want to know if there are
                // any.
                searchClient.getSearchResults(searchId, 1, 1).items.isNotEmpty()
            }
            .map { Database(it, it.id.toString(), it.name) }
            .withPageInfo()

    // FIXME the ODD model seems broken. See comment with listDatabases.
    private fun getDatabase(databaseId: String): Resource {
        return dataSources.values
            .filter { dataSource ->
                val searchId = searchDataSetsInDataSource(dataSource).searchId
                // we try for one search result on the first page, we only want to know if there are
                // any.
                searchClient.getSearchResults(searchId, 1, 1).items.isNotEmpty()
            }
            .map { Database(it, it.id.toString(), it.name) }
            .withPageInfo<Resource>()
            .data
            .find { it.id == databaseId }
            ?: throw ResourceException(
                ResourceException.Code.NOT_FOUND,
                ResourceInfo.newBuilder()
                    .setResourceType("Database")
                    .setResourceName(databaseId)
                    .setDescription("Database $databaseId not found in catalog $id")
                    .setOwner("Catalog: $id")
                    .build()
            )
    }

    private inner class Database(
        val dataSource: DataSource,
        override val id: String,
        override val displayName: String
    ) : Resource {
        override fun fqn(): String = id

        /*
            Just returning a hardcoded schema with id 'schema' and name the same as that of the dataSource.
        */
        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> =
            listOf(Schema(this, "schema", dataSource.name)).withPageInfo()

        override suspend fun getChild(childId: String): Resource =
            listChildren(THOUSAND_RECORDS).data.firstOrNull { it.id == childId }
                ?: throw ResourceException(
                    ResourceException.Code.NOT_FOUND,
                    ResourceInfo.newBuilder()
                        .setResourceType("Catalog Database Schema")
                        .setResourceName(childId)
                        .setDescription("Schema $childId not found in database $id")
                        .setOwner("Database: $id")
                        .build()
                )
    }

    private inner class Schema(
        val database: Database,
        override val id: String,
        override val displayName: String,
    ) : Resource {
        override fun fqn(): String = id

        override suspend fun listChildren(
            pageParameters: PageParameters
        ): PagedCollection<Resource> {
            val searchId = searchDataSetsInDataSource(database.dataSource).searchId
            val tables =
                getAllSearchResults(searchId).map { Table(this, "${it.id}", it.externalName) }
            return tables.withPageInfo()
        }

        override suspend fun getChild(childId: String): Resource {
            return listChildren(THOUSAND_RECORDS).data.firstOrNull { it.id == childId }
                ?: throw ResourceException(
                    ResourceException.Code.NOT_FOUND,
                    ResourceInfo.newBuilder()
                        .setResourceType("Table")
                        .setResourceName(childId)
                        .setDescription("Table $childId not found in schema $id")
                        .setOwner("Schema: $id")
                        .build()
                )
        }
    }

    private inner class Table(
        private val schema: Schema,
        override val id: String,
        override val displayName: String
    ) : LeafResource() {
        private fun getAllParents(parentId: Long, fieldsById: Map<Long, DataSetField>): List<Long> {
            val parent = checkNotNull(fieldsById[parentId]) { "The parent field should exist" }
            return if (parent.parentFieldId != null) {
                listOf(parent.id) + getAllParents(parent.parentFieldId, fieldsById)
            } else {
                listOf(parent.id)
            }
        }

        override suspend fun createBlueprint(): DataPolicy {
            val datasetStructure = datasetsClient.getDataSetStructureLatest(id.toLong())
            val fields = datasetStructure.fieldList
            val fieldsById = fields.associateBy { it.id }

            val policyBuilder = DataPolicy.newBuilder()

            policyBuilder.sourceBuilder.addAllFields(
                fields.map { field ->
                    val fieldPath =
                        if (field.parentFieldId != null) {
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
                        .build()
                        .normalizeType()
                },
            )
            with(policyBuilder.metadataBuilder) {
                title = displayName
                description = schema.database.displayName
            }
            return policyBuilder.build()
        }

        override fun fqn(): String {
            return id
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

    override fun close() {}

    /** search all the datasets in one particular dataSource. */
    private fun searchDataSetsInDataSource(dataSource: DataSource): SearchFacetsData =
        searchClient.search(
            SearchFormData(
                query = "",
                filters =
                    SearchFormDataFilters(
                        entityClasses =
                            listOf(
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
                        datasources =
                            listOf(
                                SearchFilterState(
                                    entityName = dataSource.name,
                                    entityId = dataSource.id,
                                    selected = true,
                                )
                            )
                    ),
            ),
        )

    /** recursively get all results for a given search id. TODO handle paging. */
    private fun getAllSearchResults(
        searchId: UUID,
        page: Int = 1,
        size: Int = 100
    ): List<DataEntity> {
        val searchResults = searchClient.getSearchResults(searchId, page, size)

        return if (searchResults.items.size == size) {
            searchResults.items + getAllSearchResults(searchId, page + 1, size)
        } else {
            searchResults.items
        }
    }
}
