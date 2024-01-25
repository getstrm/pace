package com.getstrm.pace.catalogs

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog as ApiCatalog
import build.buf.gen.getstrm.pace.api.entities.v1alpha.ResourceUrn
import build.buf.gen.getstrm.pace.api.entities.v1alpha.resourceUrn
import com.getstrm.pace.config.CatalogConfiguration
import com.getstrm.pace.domain.IntegrationClient

/** Abstraction of the physical data concepts in a data catalog. */
abstract class DataCatalog(val config: CatalogConfiguration) : AutoCloseable, IntegrationClient() {
    override val id: String
        get() = config.id

    override val resourceUrn: ResourceUrn
        get() = resourceUrn { catalog = apiCatalog }

    val type: ApiCatalog.Type
        get() = config.type

    val apiCatalog: ApiCatalog
        get() = ApiCatalog.newBuilder().setId(id).setType(config.type).build()
}
