val generatedBufDependencyVersion: String by rootProject.extra
val kotestVersion = rootProject.ext["kotestVersion"] as String
val springBootVersion = rootProject.extra["springBootVersion"] as String

project.version =
    if (gradle.startParameter.taskNames.any { it.lowercase() == "builddocker" }) {
        "${project.version}-SNAPSHOT"
    } else {
        "${project.version}"
    }

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.diffplug.spotless") version "6.25.0"
}

dependencies {
    // A few Spring-related dependencies, even though we don't start a Spring application in this module.
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-jooq")

    // Dependencies managed by Spring
    // TODO remove once we upgrade Spring: override SnakeYAML dependency, as the one managed by
    // Spring is too old and is vulnerable
    implementation(project(":core"))
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    // TODO Remove once this bug is fixed: https://github.com/Kotlin/kotlinx.coroutines/issues/3958
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Self-managed dependencies
    implementation("com.databricks:databricks-sdk-java:0.17.1")
    implementation("com.github.drapostolos:type-parser:0.8.1")
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.4.2.jre11")

    implementation(enforcedPlatform("com.google.cloud:libraries-bom:26.31.0"))
    implementation("com.google.cloud:google-cloud-bigquery")
    implementation("com.google.cloud:google-cloud-datacatalog")
    implementation("com.google.cloud:google-cloud-datalineage")

    implementation("build.buf.gen:getstrm_pace_grpc_java:1.61.0.1.$generatedBufDependencyVersion")
    implementation("build.buf.gen:getstrm_pace_grpc_kotlin:1.4.1.1.$generatedBufDependencyVersion")
    implementation(
        "build.buf.gen:getstrm_pace_protocolbuffers_java:25.2.0.1.$generatedBufDependencyVersion"
    )
    implementation("build.buf:protovalidate:0.1.9")

    implementation(platform("io.ktor:ktor-bom:2.3.8"))

    // Test dependencies
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.mockk:mockk:1.13.9")
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
        // FIXME enable again!
        isEnforceCheck = false
        ktfmt().kotlinlangStyle() // kotlinlangStyle guarantees following the kotlin style guide
    }
}

tasks.jar {
    enabled = true
}
