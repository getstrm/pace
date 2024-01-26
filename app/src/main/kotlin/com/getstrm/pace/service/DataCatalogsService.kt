package com.getstrm.pace.service

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataCatalog as ApiCatalog
import com.getstrm.pace.catalogs.CollibraCatalog
import com.getstrm.pace.catalogs.DataCatalog
import com.getstrm.pace.catalogs.DatahubCatalog
import com.getstrm.pace.catalogs.OpenDataDiscoveryCatalog
import com.getstrm.pace.config.AppConfiguration
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DataCatalogsService(catalogsConfig: AppConfiguration) {
    private val log by lazy { LoggerFactory.getLogger(javaClass) }

    val catalogs: Map<String, DataCatalog> =
        catalogsConfig.catalogs
            .mapNotNull { config ->
                try {
                    when (config.type) {
                        ApiCatalog.Type.TYPE_UNSPECIFIED -> null
                        ApiCatalog.Type.COLLIBRA -> CollibraCatalog(config)
                        ApiCatalog.Type.ODD -> OpenDataDiscoveryCatalog(config)
                        ApiCatalog.Type.DATAHUB -> DatahubCatalog(config)
                        ApiCatalog.Type.UNRECOGNIZED -> null
                    }
                } catch (e: Exception) {
                    log.warn(
                        "Can't instantiate DataCatalog ${config.id}/${config.type}: {}",
                        e.message
                    )
                    null
                }
            }
            .associateBy { it.id }
}
