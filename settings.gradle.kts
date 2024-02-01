rootProject.name = "pace"
//include( ":domain", ":app", ":dbt")
include( ":domain", ":dbt", ":app")

pluginManagement {
    val kotlinVersion: String by settings
    val flywayVersion: String by settings
    val springBootVersion: String by settings

    plugins {
        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version "1.1.4"
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.spring") version kotlinVersion

        id("com.google.cloud.artifactregistry.gradle-plugin") version "2.2.1"
        id("nu.studer.jooq") version "9.0"
        id("org.openapi.generator") version "7.2.0"
        id("com.bmuschko.docker-remote-api") version "9.4.0"
        id("org.flywaydb.flyway") version flywayVersion
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

buildscript {
     val kotlinVersion: String by settings

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }

    plugins {
        id("com.google.cloud.artifactregistry.gradle-plugin")
    }
}
