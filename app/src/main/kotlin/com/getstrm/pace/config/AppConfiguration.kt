package com.getstrm.pace.config

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppConfiguration(
    val exposeApplicationExceptions: Boolean = false,
    val defaultViewSuffix: String = "",
    val catalogs: List<CatalogConfiguration> = emptyList(),
    val dataPolicies: DataPoliciesConfiguration = DataPoliciesConfiguration(),
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

data class DataPoliciesConfiguration(
    val autoIncrementVersion: Boolean = false,
)
