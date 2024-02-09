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

dependencies {
    // A few Spring-related dependencies, even though we don't start a Spring application in this
    // module.
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("net.devh:grpc-server-spring-boot-starter:2.15.0.RELEASE")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.h2database:h2")
    // TODO Remove once this bug is fixed: https://github.com/Kotlin/kotlinx.coroutines/issues/3958
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // Self-managed dependencies
    implementation("com.github.drapostolos:type-parser:0.8.1")
    implementation("com.google.protobuf:protobuf-java-util:3.25.2")
    implementation(
        "build.buf.gen:getstrm_pace_protocolbuffers_kotlin:25.2.0.1.$generatedBufDependencyVersion",
    )
    implementation("build.buf:protovalidate:0.1.9")

    // Test dependencies
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
}

tasks.findByName("spotlessApply")?.dependsOn("spotlessKotlin")

tasks.test {
    testLogging {
        // Ensures full kotest diffs are printed
        exceptionFormat = TestExceptionFormat.FULL
    }
}

tasks.jar { enabled = true }
