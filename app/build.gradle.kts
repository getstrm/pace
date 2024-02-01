import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.springframework.boot.gradle.tasks.bundling.BootJar

val buildTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

val generatedBufDependencyVersion: String by rootProject.extra
val postgresPort: Int by rootProject.extra
val kotestVersion = rootProject.ext["kotestVersion"] as String
val openDataDiscoveryOpenApiDir = layout.buildDirectory.dir("generated/source/odd").get()
val springCloudKubernetesVersion = rootProject.ext["springCloudKubernetesVersion"] as String

project.version =
    if (gradle.startParameter.taskNames.any { it.lowercase() == "builddocker" }) {
        "${project.version}-SNAPSHOT"
    } else {
        "${project.version}"
    }

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.spring")
    id("com.apollographql.apollo3") version "3.8.2"
    id("org.openapi.generator")
    id("com.diffplug.spotless") version "6.25.0"
}

dependencies {
    implementation(project(":domain"))
    // Dependencies managed by Spring
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // TODO remove once we upgrade Spring: override SnakeYAML dependency, as the one managed by
    // Spring is too old and is vulnerable
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.h2database:h2")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    // TODO Remove once this bug is fixed: https://github.com/Kotlin/kotlinx.coroutines/issues/3958
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Self-managed dependencies
    implementation("net.devh:grpc-server-spring-boot-starter:2.15.0.RELEASE")
    implementation("com.databricks:databricks-sdk-java:0.17.1")
    implementation("com.github.drapostolos:type-parser:0.8.1")
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.4.2.jre11")

    implementation(
        "org.springframework.cloud:spring-cloud-starter-kubernetes-fabric8-config:$springCloudKubernetesVersion"
    )

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
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "com.vaadin.external.google", module = "android-json")
    }
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("io.zonky.test:embedded-postgres:2.0.6")
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
        ktfmt().kotlinlangStyle() // kotlinlangStyle guarantees following the kotlin style guide
    }
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$projectDir/src/main/resources/data-catalogs/open-data-discovery/openapi.yaml")
    outputDir.set(openDataDiscoveryOpenApiDir.toString())
    apiPackage.set("org.opendatadiscovery.generated.api")
    invokerPackage.set("org.opendatadiscovery.generated.invoker")
    modelPackage.set("org.opendatadiscovery.generated.model")
    configOptions.set(
        mapOf(
            "serializationLibrary" to "jackson",
            // Required to ignore unknown properties, as the OpenAPI spec does not fully match the
            // implementation :'(
            "additionalModelTypeAnnotations" to
                "@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)"
        )
    )
}

tasks.findByName("compileKotlin")?.dependsOn("downloadCollibraApolloSchemaFromIntrospection")

tasks.findByName("compileKotlin")?.dependsOn("downloadDatahubApolloSchemaFromIntrospection")

tasks.findByName("compileKotlin")?.dependsOn("openApiGenerate")

tasks.findByName("spotlessApply")?.dependsOn("spotlessKotlin")

tasks.findByName("openApiGenerate")?.dependsOn("spotlessApply")

gradle.taskGraph.whenReady {
    val apolloTaskNameRegex = "download(.+)ApolloSchemaFromIntrospection".toRegex()

    // Disable introspection tasks if the schema file already exists
    allTasks
        .filter { "ApolloSchemaFromIntrospection" in it.name }
        .forEach {
            apolloTaskNameRegex.find(it.name)?.let { matchResult ->
                val catalogName = matchResult.groupValues[1].lowercase(Locale.getDefault())
                val catalogGraphQlSchemaFileExists =
                    File("$rootDir/app/src/main/graphql/$catalogName/schema.graphqls").exists()

                it.onlyIf { !catalogGraphQlSchemaFileExists }
            }
        }
}

kotlin {
    val kotlinMainSourceSet = sourceSets["main"].kotlin

    kotlinMainSourceSet.srcDir("$openDataDiscoveryOpenApiDir/src/main/kotlin")
}

tasks.test {
    testLogging {
        // Ensures full kotest diffs are printed
        exceptionFormat = TestExceptionFormat.FULL
    }
}

tasks.named<BootJar>("bootJar") {
    mainClass = "com.getstrm.pace.PaceApplicationKt"
    archiveFileName = "app.jar"
    manifest {
        attributes["Implementation-Title"] = "Policy As Code Engine"

        attributes["Implementation-Version"] =
            if (project.version.toString().endsWith("-SNAPSHOT")) {
                "${project.version} (built at $buildTimestamp)"
            } else {
                project.version
            }
    }
}

tasks.jar {
    // Disables the "-plain.jar" (builds only the bootJar)
    enabled = false
}

val createProtoDescriptor =
    tasks.register<Exec>("createProtoDescriptor") {
        group = "docker"
        workingDir("$rootDir/protos")
        commandLine("buf", "build", "-o", "$projectDir/build/docker/descriptor.binpb")

        // prevent creating a proto descriptor via Gradle build (done manually in CI)
        onlyIf { !(rootProject.ext.has("ciBuild")) }
    }

val copyDocker =
    tasks.register<Copy>("copyDocker") {
        group = "docker"
        val grpcServices: String =
            ByteArrayOutputStream().use { outputStream ->
                project.exec {
                    workingDir("$rootDir/protos")

                    commandLine(
                        "bash",
                        "-c",
                        "buf build -o -#format=json | jq -rc '.file | map(select(.name | startswith(\"getstrm\"))) | map(select(.service > 0) | (.package + \".\" + .service[].name))'"
                    )
                    standardOutput = outputStream
                }

                outputStream.toString()
            }

        from("src/main/docker")
        include("*")
        into("build/docker")
        expand(
            "version" to project.version,
            "grpcServices" to grpcServices,
        )
    }

val copyJarIntoDockerDir =
    tasks.register<Copy>("copyJarIntoDocker") {
        group = "docker"
        dependsOn("bootJar")

        from("build/libs/app.jar")
        into("build/docker")
    }

val prepareForDocker =
    tasks.register("prepareForDocker") {
        group = "docker"
        dependsOn("build", copyDocker, createProtoDescriptor, copyJarIntoDockerDir)
    }

val buildDocker =
    tasks.register<Exec>("buildDocker") {
        group = "docker"
        dependsOn(prepareForDocker)
        workingDir("build/docker")

        commandLine("/usr/bin/env", "docker", "build", ".", "-t", project.properties["dockertag"])
    }

apollo {
    service("collibra") {
        packageName.set("com.collibra.generated")
        sourceFolder.set("collibra")
        introspection {
            endpointUrl.set("https://test-drive.collibra.com/graphql/knowledgeGraph/v1")
            schemaFile.set(file("src/main/graphql/collibra/schema.graphqls"))
            // TODO remove creds
            headers.set(
                mapOf(
                    "Authorization" to "Basic dGVzdGRyaXZldXNlcjlvMHY4bjRuOk9vY2Vtb2ckNW01cjduOGQ="
                )
            )
        }
    }

    service("datahub") {
        packageName.set("io.datahubproject.generated")
        sourceFolder.set("datahub")
        introspection {
            endpointUrl.set("http://datahub-datahub-frontend.datahub:9002/api/graphql")
            schemaFile.set(file("src/main/graphql/datahub/schema.graphqls"))
            // TODO remove creds
            headers.set(
                mapOf(
                    "Authorization" to
                        "Bearer eyJhbGciOiJIUzI1NiJ9.eyJhY3RvclR5cGUiOiJVU0VSIiwiYWN0b3JJZCI6ImRhdGFodWIiLCJ0eXBlIjoiUEVSU09OQUwiLCJ2ZXJzaW9uIjoiMiIsImp0aSI6IjE4YWExMjA3LWY2NTQtNDc4OS05MTU3LTkwYTMyMjExMWJkYyIsInN1YiI6ImRhdGFodWIiLCJpc3MiOiJkYXRhaHViLW1ldGFkYXRhLXNlcnZpY2UifQ.8-NksHdL4p3o9_Bryst2MOvH-bATl-avC8liB-E2_sM"
                )
            )
        }
    }
}
