import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val generatedBufDependencyVersion: String by rootProject.extra
val kotestVersion = rootProject.ext["kotestVersion"] as String
val springBootVersion = rootProject.extra["springBootVersion"] as String
val protobufJavaUtilVersion = rootProject.extra["protobufJavaUtilVersion"] as String

project.version =
    if (gradle.startParameter.taskNames.any { it.lowercase() == "builddocker" }) {
        "${project.version}-SNAPSHOT"
    } else {
        "${project.version}"
    }

plugins {
    id("com.github.johnrengelman.shadow")
}

dependencies {
    implementation(project(":core"))

    // We let spring boot manage a few dependencies, even though we don't start a Spring application.
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.h2database:h2")

    // Self-managed dependencies
    implementation("com.google.protobuf:protobuf-java-util:$protobufJavaUtilVersion")
    implementation("build.buf.gen:getstrm_pace_grpc_java:1.61.1.1.$generatedBufDependencyVersion")
    implementation("build.buf.gen:getstrm_pace_grpc_kotlin:1.4.1.1.$generatedBufDependencyVersion")
    implementation(
        "build.buf.gen:getstrm_pace_protocolbuffers_java:25.3.0.1.$generatedBufDependencyVersion"
    )

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "com.vaadin.external.google", module = "android-json")
    }
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
}

tasks.jar {
    enabled = false
}

val shadowJar = tasks.withType<ShadowJar> {
    isZip64 = true
    mergeServiceFiles()

    manifest {
        attributes["Main-Class"] = "com.getstrm.pace.dbt.MainKt"
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}
