import java.time.Instant
import java.time.format.DateTimeFormatter
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

val buildTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

val generatedBufDependencyVersion: String by rootProject.extra
val kotestVersion = rootProject.ext["kotestVersion"] as String

project.version =
    if (gradle.startParameter.taskNames.any { it.lowercase() == "builddocker" }) {
        "${project.version}-SNAPSHOT"
    } else {
        "${project.version}"
    }

val springBootVersion = rootProject.extra["springBootVersion"] as String

plugins { id("com.diffplug.spotless") version "6.25.0" }

dependencies {
    // A few Spring-related dependencies, even though we don't start a Spring application in this
    // module.
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("net.devh:grpc-server-spring-boot-starter:2.15.0.RELEASE")

    // TODO remove once we upgrade Spring: override SnakeYAML dependency, as the one managed by
    // Spring is too old and is vulnerable
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.postgresql:postgresql")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.h2database:h2")
    // TODO Remove once this bug is fixed: https://github.com/Kotlin/kotlinx.coroutines/issues/3958
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Self-managed dependencies
    implementation("com.databricks:databricks-sdk-java:0.17.1")
    implementation("com.github.drapostolos:type-parser:0.8.1")
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.4.2.jre11")

    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.77")

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

    implementation("com.apollographql.apollo3:apollo-runtime:3.8.2")

    implementation("com.aallam.openai:openai-client:3.6.3")
    implementation(platform("io.ktor:ktor-bom:2.3.8"))
    runtimeOnly("io.ktor:ktor-client-okhttp")
    implementation("io.ktor:ktor-client-logging")

    // Test dependencies
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("io.zonky.test:embedded-postgres:2.0.6")
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
        // FIXME enable again!
        isEnforceCheck = false
        ktfmt().kotlinlangStyle() // kotlinlangStyle guarantees following the kotlin style guide
    }
}

tasks.findByName("spotlessApply")?.dependsOn("spotlessKotlin")

tasks.test {
    testLogging {
        // Ensures full kotest diffs are printed
        exceptionFormat = TestExceptionFormat.FULL
    }
}

tasks.jar { enabled = true }
