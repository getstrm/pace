package com.getstrm.pace.config

import kotlin.reflect.full.memberProperties
import org.springframework.stereotype.Component
import org.springframework.validation.Errors
import org.springframework.validation.Validator

@Component
class ConfigurationPropertiesValidator : Validator {
    override fun supports(clazz: Class<*>): Boolean {
        return clazz == AppConfiguration::class.java
    }

    override fun validate(target: Any, errors: Errors) {
        val appConfig = target as AppConfiguration

        val catalogIds = appConfig.catalogs.map { it.id }
        val platformIds =
            ProcessingPlatformsConfiguration::class.memberProperties.flatMap {
                val processingPlatformConfig =
                    it.get(appConfig.processingPlatforms) as List<ProcessingPlatformConfiguration>
                processingPlatformConfig.map { processingPlatform -> processingPlatform.id }
            }

        val duplicateCatalogIds = catalogIds.groupBy { it }.filter { it.value.size > 1 }.keys
        val duplicateProcessingPlatformIds =
            platformIds.groupBy { it }.filter { it.value.size > 1 }.keys
        val duplicateCombined = catalogIds.toSet().intersect(platformIds.toSet())

        if (duplicateCatalogIds.isNotEmpty()) {
            errors.reject("duplicate", "Duplicate catalog ids found: $duplicateCatalogIds")
        }

        if (duplicateProcessingPlatformIds.isNotEmpty()) {
            errors.reject(
                "duplicate",
                "Duplicate platform ids found: $duplicateProcessingPlatformIds"
            )
        }

        if (duplicateCombined.isNotEmpty()) {
            errors.reject(
                "duplicate",
                "Catalog ids and platform ids must be unique, found overlapping ids: $duplicateCombined"
            )
        }
    }
}
