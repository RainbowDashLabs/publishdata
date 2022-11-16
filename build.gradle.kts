plugins {
    //kotlin("jvm") version "1.5.31"
    `kotlin-dsl`
    `maven-publish`
    `java-gradle-plugin`
}

group = "de.chojo"
version = "1.0.9"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    compileOnly(gradleApi())
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

            val isSnap = version.toString().endsWith("SNAPSHOT")
            val releasesRepoUrl = "https://eldonexus.de/repository/maven-releases/"
            val snapshotsRepoUrl = "https://eldonexus.de/repository/maven-snapshots/"
            url = uri(if (isSnap) snapshotsRepoUrl else releasesRepoUrl)
            name = "EldoNexus"
        }
    }
}

tasks {
    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}
