plugins {
    kotlin("jvm") version "1.9.25"
    `maven-publish`
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "de.chojo"
version = "1.3.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    compileOnly(gradleApi())
    implementation(kotlin("stdlib"))
}

gradlePlugin {
    plugins {
        create("publishData") {
            id = "de.chojo.publishdata"
            implementationClass = "de.chojo.PublishData"
        }
    }
}

publishing {
    repositories {
        maven {
            group = project.group as String
            authentication {
                credentials(PasswordCredentials::class) {
                    username = System.getenv("NEXUS_USERNAME")
                    password = System.getenv("NEXUS_PASSWORD")
                }
            }

            val branch = System.getenv("GITHUB_REF")?.replace("refs/heads/", "") ?: ""

            url = uri(when (branch) {
                "main", "master" -> "https://eldonexus.de/repository/maven-releases/"
                "dev" -> "https://eldonexus.de/repository/maven-dev/"
                else -> "https://eldonexus.de/repository/maven-snapshots/"
            })

            version = when (branch) {
                "main", "master" -> version
                "dev" -> version.toString().plus("-DEV")
                else -> version.toString().plus("-SNAPSHOT")
            }
            name = "EldoNexus"
        }
    }
}

tasks {
    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}
