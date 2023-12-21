rootProject.name = "pace"
include(":app")

pluginManagement {
    val kotlinVersion: String by settings

    plugins {
        id("org.springframework.boot") version "3.2.1"
        id("io.spring.dependency-management") version "1.1.4"
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.spring") version kotlinVersion

        id("com.google.cloud.artifactregistry.gradle-plugin") version "2.2.1"
        id("nu.studer.jooq") version "8.2.1"
        id("org.openapi.generator") version "7.1.0"
        id("com.bmuschko.docker-remote-api") version "9.4.0"
        id("org.flywaydb.flyway") version "9.22.3"
    }
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
