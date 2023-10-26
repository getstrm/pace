import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
import nu.studer.gradle.jooq.JooqGenerate
import org.flywaydb.gradle.task.FlywayMigrateTask
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.net.InetAddress
import java.net.Socket
import java.util.*
import kotlin.system.exitProcess

val generatedBufDependencyVersion: String by rootProject.extra
val jooqGenerationPath = layout.buildDirectory.dir("generated/source/jooq/pace").get()
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
    // Dependencies managed by Spring
    implementation("org.springframework.boot:spring-boot-starter-jooq")
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

    // Self-managed dependencies
    implementation("net.devh:grpc-server-spring-boot-starter:2.15.0.RELEASE")
    implementation("com.databricks:databricks-sdk-java:0.10.0")

    implementation("com.nimbusds:nimbus-jose-jwt:9.37")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.76")

    // Todo remove before squashing - why?
    implementation(enforcedPlatform("com.google.cloud:libraries-bom:26.24.0"))
    implementation("com.google.cloud:google-cloud-bigquery")

    implementation("build.buf.gen:getstrm_pace_grpc_java:1.58.0.1.$generatedBufDependencyVersion")
    implementation("build.buf.gen:getstrm_pace_grpc_kotlin:1.3.1.1.$generatedBufDependencyVersion")
    implementation("build.buf.gen:getstrm_pace_protocolbuffers_java:24.4.0.1.$generatedBufDependencyVersion")

    implementation("com.apollographql.apollo3:apollo-runtime:3.8.2")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.7.2")
    testImplementation("io.mockk:mockk:1.13.8")
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

tasks.named<BootJar>("bootJar") {
    mainClass =  "com.getstrm.pace.PaceApplicationKt"
    manifest {
        attributes["Implementation-Title"] = "Policy and Contract Engine"
        attributes["Implementation-Version"] = version
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
        targetImageId("postgres")
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
        if (rootProject.ext.has("ciBuild")) {
            workingDir("$rootDir/generated")
            commandLine("cp", "descriptor.binpb", "$projectDir/build/docker/descriptor.binpb")
        } else {
            workingDir("$rootDir/protos")
            commandLine(
                "buf",
                "build",
                "-o",
                "$projectDir/build/docker/descriptor.binpb"
            )
        }
    }

val copyDocker =
    tasks.register<Copy>("copyDocker") {
        group = "docker"
        from("src/main/docker")
        include("*")
        into("build/docker")
        expand(
            "version" to project.version,
        )
    }

val copyJarIntoDockerDir =
    tasks.register<Copy>("copyJarIntoDocker") {
        group = "docker"
        dependsOn("bootJar")

        val bootJar: BootJar by tasks

        from("build/libs/${bootJar.archiveFileName.get()}")
        rename(".*.jar", "app.jar")
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

        if (System.getProperty("os.name") == "Mac OS X" && System.getProperty("os.arch") == "aarch64") {
            println("Building docker image on ARM Mac system. If you don't have a cross platform builder instance yet, create one using:")
            println("  docker buildx create --use")
            commandLine(
                "/usr/bin/env",
                "docker",
                "buildx",
                "build",
                "--platform",
                "linux/amd64",
                "-t",
                project.properties["dockertag"],
                "."
            )
        } else {
            commandLine("/usr/bin/env", "docker", "build", ".", "-t", project.properties["dockertag"])
        }
    }

val pushDocker = tasks.register<Exec>("pushDocker") {
    group = "docker"
    dependsOn(buildDocker)
    workingDir("build/docker")

    commandLine("/usr/bin/env", "docker", "push", project.properties["dockertag"])
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

