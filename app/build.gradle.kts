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

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.spring")
    id("com.bmuschko.docker-remote-api")
    id("nu.studer.jooq")
    id("org.flywaydb.flyway")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.flywaydb:flyway-core")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.postgresql:postgresql")
    implementation("net.devh:grpc-server-spring-boot-starter:2.15.0.RELEASE")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("io.micrometer:micrometer-registry-prometheus")
    jooqGenerator("org.postgresql:postgresql")

    // Todo remove before squashing
    implementation("io.strmprivacy.grpc.common:kotlin-grpc-common:3.22.0")

    implementation("io.strmprivacy.api:api-definitions-kotlin:3.18.4")
    implementation("build.buf.gen:getstrm_daps_grpc_java:1.58.0.1.$generatedBufDependencyVersion")
    implementation("build.buf.gen:getstrm_daps_grpc_kotlin:1.3.1.1.$generatedBufDependencyVersion")
    implementation("build.buf.gen:getstrm_daps_protocolbuffers_java:24.4.0.1.$generatedBufDependencyVersion")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<BootJar>("bootJar") {
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
    version.set(dependencyManagement.importedProperties["jooq.version"] as String)

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
                        packageName = "io.strmprivacy.jooq.generated"
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

