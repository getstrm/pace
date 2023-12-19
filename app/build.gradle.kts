import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
import nu.studer.gradle.jooq.JooqGenerate
import org.flywaydb.gradle.task.FlywayMigrateTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.Socket
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.system.exitProcess

val buildTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

val generatedBufDependencyVersion: String by rootProject.extra
val jooqGenerationPath = layout.buildDirectory.dir("generated/source/jooq/pace").get()
val postgresPort: Int by rootProject.extra
val jooqSchema = rootProject.extra["schema"] as String
val jooqMigrationDir = "$projectDir/src/main/resources/db/migration/postgresql"
val jooqVersion = rootProject.ext["jooqVersion"] as String
val kotestVersion = rootProject.ext["kotestVersion"] as String
val openDataDiscoveryOpenApiDir = layout.buildDirectory.dir("generated/source/odd").get()
val springCloudKubernetesVersion = rootProject.ext["springCloudKubernetesVersion"] as String
project.version = if (gradle.startParameter.taskNames.any { it.lowercase() == "builddocker" }) {
    "${project.version}-SNAPSHOT"
} else {
    "${project.version}"
}

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.spring")
    id("com.bmuschko.docker-remote-api")
    id("com.apollographql.apollo3") version "3.8.2"
    id("nu.studer.jooq")
    id("org.flywaydb.flyway")
    id("org.openapi.generator")
}

dependencies {
    // Dependencies managed by Spring
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    // TODO remove once we upgrade Spring: override SnakeYAML dependency, as the one managed by Spring is too old and is vulnerable
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.flywaydb:flyway-core")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("io.micrometer:micrometer-registry-prometheus")
    jooqGenerator("org.postgresql:postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.h2database:h2")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    // TODO Remove once this bug is fixed: https://github.com/Kotlin/kotlinx.coroutines/issues/3958
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Self-managed dependencies
    implementation("net.devh:grpc-server-spring-boot-starter:2.15.0.RELEASE")
    implementation("com.databricks:databricks-sdk-java:0.13.0")
    implementation("com.github.drapostolos:type-parser:0.8.1")
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.4.2.jre11")

    implementation("org.springframework.cloud:spring-cloud-starter-kubernetes-fabric8-config:$springCloudKubernetesVersion")

    implementation("com.nimbusds:nimbus-jose-jwt:9.37.2")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.77")

    implementation(enforcedPlatform("com.google.cloud:libraries-bom:26.29.0"))
    implementation("com.google.cloud:google-cloud-bigquery")
    implementation("com.google.cloud:google-cloud-datacatalog")

    implementation("build.buf.gen:getstrm_pace_grpc_java:1.60.0.1.$generatedBufDependencyVersion")
    implementation("build.buf.gen:getstrm_pace_grpc_kotlin:1.4.1.1.$generatedBufDependencyVersion")
    implementation("build.buf.gen:getstrm_pace_protocolbuffers_java:25.1.0.1.$generatedBufDependencyVersion")
    implementation("build.buf:protovalidate:0.1.8")

    implementation("com.apollographql.apollo3:apollo-runtime:3.8.2")

    implementation("com.aallam.openai:openai-client:3.6.2")
    implementation(platform("io.ktor:ktor-bom:2.3.7"))
    runtimeOnly("io.ktor:ktor-client-okhttp")
    implementation("io.ktor:ktor-client-logging")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "com.vaadin.external.google", module = "android-json")
    }
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.zonky.test:embedded-postgres:2.0.6")
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
            // Required to ignore unknown properties, as the OpenAPI spec does not fully match the implementation :'(
            "additionalModelTypeAnnotations" to "@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)"
        )
    )
}

tasks.findByName("compileKotlin")?.dependsOn("downloadCollibraApolloSchemaFromIntrospection")
tasks.findByName("compileKotlin")?.dependsOn("downloadDatahubApolloSchemaFromIntrospection")
tasks.findByName("compileKotlin")?.dependsOn("openApiGenerate")

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

        attributes["Implementation-Version"] = if (project.version.toString().endsWith("-SNAPSHOT")) {
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

val removePostgresContainer =
    tasks.register("jooqPostgresRemove", DockerRemoveContainer::class) {
        group = "postgres"
        targetContainerId("jooq-postgres")
        force.set(true)
        onError {
            // noop
        }
    }

val createPostgresContainer =
    tasks.register("jooqPostgresCreate", DockerCreateContainer::class) {
        dependsOn(removePostgresContainer)
        group = "postgres"
        targetImageId("postgres:15")
        containerName.set("jooq-postgres")
        hostConfig.portBindings.set(listOf("$postgresPort:5432"))
        hostConfig.autoRemove.set(true)
        envVars.set(
            mapOf(
                "POSTGRES_USER" to "postgres",
                "POSTGRES_PASSWORD" to "postgres",
                "POSTGRES_DB" to "postgres"
            )
        )
        attachStderr.set(true)
        attachStdout.set(true)
        tty.set(true)
    }

val startPostgresContainer =
    tasks.register("jooqPostgresStart", DockerStartContainer::class) {
        group = "postgres"
        dependsOn(createPostgresContainer)
        targetContainerId("jooq-postgres")
        doLast {
            val tries = 100
            for (i in 1..tries) {
                try {
                    Socket(InetAddress.getLocalHost().hostName, postgresPort)
                    // It might take postgres a while to be ready for connections
                    project.logger.lifecycle("Connection to port $postgresPort succeeded, waiting 2 seconds for Postgres to finish initialization")
                    Thread.sleep(2000)
                    break
                } catch (e: Exception) {
                    Thread.sleep(100)
                }
                if (i == tries) {
                    project.logger.lifecycle("No connection to port $postgresPort was established")
                    exitProcess(1)
                }
            }
        }
    }

val stopPostgresContainer =
    tasks.register("jooqPostgresStop", DockerStopContainer::class) {
        group = "postgres"
        targetContainerId("jooq-postgres")
    }

val migrateTask =
    tasks.register("jooqMigrate", FlywayMigrateTask::class) {
        dependsOn(startPostgresContainer)
        setGroup("flyway")

        driver = "org.postgresql.Driver"
        url = "jdbc:postgresql://localhost:$postgresPort/postgres"
        user = "postgres"
        password = "postgres"
        locations = arrayOf("filesystem:$jooqMigrationDir")
    }

jooq {
    group = "jooq"
    version.set(jooqVersion)

    configurations {
        create("main") {
            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.WARN
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = "jdbc:postgresql://localhost:$postgresPort/postgres"
                    user = "postgres"
                    password = "postgres"
                }
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = rootProject.extra["schema"] as String
                        includes = ".*"
                    }
                    target.apply {
                        packageName = "com.getstrm.jooq.generated"
                        directory = jooqGenerationPath.asFile.absolutePath
                    }
                }
            }
        }
    }
}

tasks.named<JooqGenerate>("generateJooq") {
    dependsOn(migrateTask)
    allInputsDeclared.set(true)
    setFakeOutputFileIn(jooqGenerationPath)
    inputs.dir(jooqMigrationDir)
}

gradle.taskGraph.whenReady {
    // Disable starting postgres in docker in CI
    allTasks
        .filter { it.group == "postgres" }
        .forEach { it.onlyIf { !(rootProject.ext.has("ciBuild")) } }

    // Set inputs and outputs for all tasks for Jooq generation
    allTasks
        .filter { it.group in listOf("postgres", "flyway", "jooq") }
        .forEach {
            it.inputs.dir(jooqMigrationDir)
            it.setFakeOutputFileIn(jooqGenerationPath)
        }
}

fun Task.setFakeOutputFileIn(path: Directory) {
    val taskOutput = path.dir("..")

    doLast {
        taskOutput.file(".gradle-task-$name").asFile.createNewFile()
    }
    outputs.file(taskOutput.file(".gradle-task-$name"))
}

val createProtoDescriptor =
    tasks.register<Exec>("createProtoDescriptor") {
        group = "docker"
        workingDir("$rootDir/protos")
        commandLine(
            "buf",
            "build",
            "-o",
            "$projectDir/build/docker/descriptor.binpb"
        )

        // prevent creating a proto descriptor via Gradle build (done manually in CI)
        onlyIf { !(rootProject.ext.has("ciBuild")) }
    }

val copyDocker =
    tasks.register<Copy>("copyDocker") {
        group = "docker"
        val grpcServices: String = ByteArrayOutputStream().use { outputStream ->
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
            headers.set(mapOf("Authorization" to "Basic dGVzdGRyaXZldXNlcjlvMHY4bjRuOk9vY2Vtb2ckNW01cjduOGQ="))
        }
    }

    service("datahub") {
        packageName.set("io.datahubproject.generated")
        sourceFolder.set("datahub")
        introspection {
            endpointUrl.set("http://datahub-datahub-frontend.datahub:9002/api/graphql")
            schemaFile.set(file("src/main/graphql/datahub/schema.graphqls"))
            // TODO remove creds
            headers.set(mapOf("Authorization" to "Bearer eyJhbGciOiJIUzI1NiJ9.eyJhY3RvclR5cGUiOiJVU0VSIiwiYWN0b3JJZCI6ImRhdGFodWIiLCJ0eXBlIjoiUEVSU09OQUwiLCJ2ZXJzaW9uIjoiMiIsImp0aSI6IjE4YWExMjA3LWY2NTQtNDc4OS05MTU3LTkwYTMyMjExMWJkYyIsInN1YiI6ImRhdGFodWIiLCJpc3MiOiJkYXRhaHViLW1ldGFkYXRhLXNlcnZpY2UifQ.8-NksHdL4p3o9_Bryst2MOvH-bATl-avC8liB-E2_sM"))
        }
    }
}
