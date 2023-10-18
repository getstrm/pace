rootProject.name = "data-policy-service"
include(":app")

pluginManagement {
    val kotlinVersion: String by settings

    plugins {
        id("org.springframework.boot") version "3.1.4"
        id("io.spring.dependency-management") version "1.1.3"
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.spring") version kotlinVersion

        id("com.google.cloud.artifactregistry.gradle-plugin") version "2.2.1"
        id("nu.studer.jooq") version "8.2.1"
        id("org.openapi.generator") version "7.0.1"
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
