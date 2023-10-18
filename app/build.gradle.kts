import org.springframework.boot.gradle.tasks.bundling.BootJar

val generatedBufDependencyVersion = rootProject.extra["generatedBufDependencyVersion"] as String

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.spring")
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

