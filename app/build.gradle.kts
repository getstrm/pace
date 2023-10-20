import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
import nu.studer.gradle.jooq.JooqGenerate
import org.flywaydb.gradle.task.FlywayMigrateTask
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.net.InetAddress
import java.net.Socket
import kotlin.system.exitProcess

val generatedBufDependencyVersion: String by rootProject.extra
val jooqGenerationPath = layout.buildDirectory.dir("generated/source/jooq/data_policy_service").get()
val postgresPort: Int by rootProject.extra
val jooqSchema = rootProject.extra["schema"] as String
val jooqMigrationDir = "$projectDir/src/main/resources/db/migration/postgresql"
val jooqVersion = rootProject.ext["jooqVersion"] as String
val openDataDiscoveryOpenApiDir = layout.buildDirectory.dir("generated/source/odd").get()


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
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.flywaydb:flyway-core")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.postgresql:postgresql")
    // Todo: maybe we can use grpc-spring-boot-starter instead to also create gRPC clients
    implementation("net.devh:grpc-server-spring-boot-starter:2.15.0.RELEASE")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("io.micrometer:micrometer-registry-prometheus")
    jooqGenerator("org.postgresql:postgresql")
    implementation("com.databricks:databricks-sdk-java:0.10.0")

    // fixme Only for login to snowflake
    implementation("org.seleniumhq.selenium:selenium-java:4.14.1")
    implementation("io.github.bonigarcia:webdrivermanager:5.5.3")


    // Todo remove before squashing
    implementation("io.strmprivacy.grpc.common:kotlin-grpc-common:3.22.0")

    implementation(enforcedPlatform("com.google.cloud:libraries-bom:26.24.0"))
    implementation("com.google.cloud:google-cloud-bigquery")

    implementation("build.buf.gen:getstrm_daps_grpc_java:1.58.0.1.$generatedBufDependencyVersion")
    implementation("build.buf.gen:getstrm_daps_grpc_kotlin:1.3.1.1.$generatedBufDependencyVersion")
    implementation("build.buf.gen:getstrm_daps_protocolbuffers_java:24.4.0.1.$generatedBufDependencyVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    implementation("com.apollographql.apollo3:apollo-runtime:3.8.2")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/data-catalogs/open-data-discovery/openapi.yaml")
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

kotlin {
    val kotlinMainSourceSet = sourceSets["main"].kotlin

    kotlinMainSourceSet.srcDir("$openDataDiscoveryOpenApiDir/src/main/kotlin")
}

tasks.named<BootJar>("bootJar") {
    mainClass =  "com.getstrm.daps.ApplicationKt"
    manifest {
        attributes["Implementation-Title"] = "Data Policy Service"
        attributes["Implementation-Version"] = version
    }
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
        targetImageId("postgres:12")
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


apollo {
    service("collibra") {
        packageName.set("com.collibra.generated")
        sourceFolder.set("collibra")
        introspection {
            endpointUrl.set("https://test-drive.collibra.com/graphql/knowledgeGraph/v1")
            schemaFile.set(file("src/main/graphql/collibra/schema.graphqls"))
            headers.set(mapOf("Authorization" to "Basic dGVzdGRyaXZldXNlcjlvMHY4bjRuOk9vY2Vtb2ckNW01cjduOGQ="))
        }
    }

    service("datahub") {
        packageName.set("io.datahubproject.generated")
        sourceFolder.set("datahub")
        introspection {
            endpointUrl.set("http://datahub-datahub-frontend.datahub:9002/api/graphql")
            schemaFile.set(file("src/main/graphql/datahub/schema.graphqls"))
            headers.set(mapOf("Authorization" to "Bearer eyJhbGciOiJIUzI1NiJ9.eyJhY3RvclR5cGUiOiJVU0VSIiwiYWN0b3JJZCI6ImRhdGFodWIiLCJ0eXBlIjoiUEVSU09OQUwiLCJ2ZXJzaW9uIjoiMiIsImp0aSI6IjE4YWExMjA3LWY2NTQtNDc4OS05MTU3LTkwYTMyMjExMWJkYyIsInN1YiI6ImRhdGFodWIiLCJpc3MiOiJkYXRhaHViLW1ldGFkYXRhLXNlcnZpY2UifQ.8-NksHdL4p3o9_Bryst2MOvH-bATl-avC8liB-E2_sM"))
        }
    }
}

