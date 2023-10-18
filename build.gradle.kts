import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
}

allprojects {

    group = "com.getstrm"
    version = rootProject.version

    apply {
        plugin("org.jetbrains.kotlin.jvm")
    }

    buildscript {
        repositories {
            mavenLocal()
            mavenCentral()

            // TODO remove this once we go open source
            maven("artifactregistry://europe-west4-maven.pkg.dev/stream-machine-development/snapshot")
            maven("artifactregistry://europe-west4-maven.pkg.dev/stream-machine-development/release")
            maven("https://buf.build/gen/maven")
        }
    }

    repositories {
        mavenLocal()
        mavenCentral()

        maven("artifactregistry://europe-west4-maven.pkg.dev/stream-machine-development/snapshot")
        maven("artifactregistry://europe-west4-maven.pkg.dev/stream-machine-development/release")
        maven("https://buf.build/gen/maven")
    }

    java.targetCompatibility = JavaVersion.VERSION_17

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
