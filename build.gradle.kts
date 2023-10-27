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
}

ext["postgresPort"] = if (ext.has("ciBuild")) 5432 else 5444
