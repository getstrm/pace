import java.time.Instant
import java.time.format.DateTimeFormatter
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

val buildTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

val generatedBufDependencyVersion: String by rootProject.extra
val protobufJavaUtilVersion = rootProject.extra["protobufJavaUtilVersion"] as String
val kotestVersion = rootProject.ext["kotestVersion"] as String

project.version =
    if (gradle.startParameter.taskNames.any { it.lowercase() == "builddocker" }) {
        "${project.version}-SNAPSHOT"
    } else {
        "${project.version}"
    }

val springBootVersion = rootProject.extra["springBootVersion"] as String

dependencies {
    // We let spring boot manage a few dependencies, even though we don't start a Spring application from this module.
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("net.devh:grpc-server-spring-boot-starter:2.15.0.RELEASE")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

    // Self-managed dependencies
    implementation("com.github.drapostolos:type-parser:0.8.1")
    implementation("com.google.protobuf:protobuf-java-util:$protobufJavaUtilVersion")
    implementation(
        "build.buf.gen:getstrm_pace_protocolbuffers_kotlin:28.2.0.1.$generatedBufDependencyVersion",
    )
    implementation("build.buf:protovalidate:0.2.0")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "com.vaadin.external.google", module = "android-json")
    }
    testImplementation("com.h2database:h2")
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
