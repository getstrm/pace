import com.fasterxml.jackson.databind.util.NativeImageUtil
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.graalvm.buildtools.gradle.dsl.NativeImageOptions
import org.gradle.internal.impldep.org.bouncycastle.its.asn1.EndEntityType.app
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.springframework.boot.loader.tools.MainClassFinder

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
    id("org.graalvm.buildtools.native") version "0.9.28"
    id("application")
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

application {
    mainClass.set("com.getstrm.pace.dbt.MainKt")
}

graalvmNative {
    binaries {
        getByName("main") {
            imageName.set("pace-dbt")
            classpath("$projectDir/build/classes/kotlin/main/")
            mainClass.set("com.getstrm.pace.dbt.MainKt")
            buildArgs.add("--initialize-at-build-time=org.slf4j.impl.StaticLoggerBinder,org.slf4j.LoggerFactory,ch.qos.logback.classic.Logger,ch.qos.logback.core.spi.AppenderAttachableImpl,ch.qos.logback.core.status.StatusBase,ch.qos.logback.classic.Level,ch.qos.logback.core.status.InfoStatus,ch.qos.logback.classic.PatternLayout,ch.qos.logback.core.CoreConstants,ch.qos.logback.core.util.Loader,ch.qos.logback.core.util.StatusPrinter")

        }
    }
    toolchainDetection = false
}
