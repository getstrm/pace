package com.getstrm.daps.config

import build.buf.gen.getstrm.api.data_policies.v1alpha.DataCatalog
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class CatalogsConfiguration(
    val catalogs: List<CatalogConfiguration> = emptyList(),
)

data class CatalogConfiguration(
    val type: DataCatalog.Type,
    val id: String,
    val serverUrl: String,
    val token: String?,
    val userName: String?,
    val password: String?,
    val fetchSize: Int? = 1,
)
