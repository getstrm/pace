import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management") apply false
}

allprojects {

    group = "com.getstrm"
    version = rootProject.version

    apply {
        plugin("org.jetbrains.kotlin.jvm")
        plugin("org.jetbrains.kotlin.plugin.spring")
        plugin("org.springframework.boot")
        plugin("io.spring.dependency-management")
    }

    buildscript {
        repositories {
            mavenLocal()
            mavenCentral()

            maven("https://buf.build/gen/maven")
        }
    }

    repositories {
        mavenLocal()
        mavenCentral()

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

    tasks.withType<BootJar> {
        enabled = false
    }
}

ext["postgresPort"] = if (ext.has("ciBuild")) 5432 else 5444


