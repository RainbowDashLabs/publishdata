![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/RainbowDashLabs/publishdata/verify.yml?style=for-the-badge&label=Building)
![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/RainbowDashLabs/publishdata/publish_to_nexus.yml?style=for-the-badge&label=Publishing) \
![Sonatype Nexus (Releases)](https://img.shields.io/nexus/maven-releases/de.chojo/publishdata?label=Release&logo=Release&server=https%3A%2F%2Feldonexus.de&style=for-the-badge)
![Sonatype Nexus (Development)](https://img.shields.io/nexus/maven-dev/de.chojo/publishdata?label=DEV&logo=Release&server=https%3A%2F%2Feldonexus.de&style=for-the-badge)
![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/de.chojo/publishdata?color=orange&label=Snapshot&server=https%3A%2F%2Feldonexus.de&style=for-the-badge)

Small gradle plugin which I use to avoid boilerplate code for version detection.

This util helps to publish artifacts, sets the correction version with optional commit hash and branch identifier and
chooses the repository based on the current branch.

## Setup

**settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        maven("https://eldonexus.de/repository/maven-public/")
    }
}
```

**build.gradle.kts**

```kotlin
plugins {
    id("de.chojo.publishdata") version "version"
}

publishData {
    // only if you want to publish to the eldonexus. If you call this you will not need to manually add repositories
    useEldoNexusRepos()
    // only if you want to publish to the gitlab. If you call this you will not need to manually add repositories
    useGitlabReposForProject("177", "https://gitlab.example.com/")
    // manually register a release repo.
    addRepo(Repo(Regex("master"), "", "https://my-repo.com/releases", false))
    // manually register a snapshot repo which will append -SNAPSHOT+<commit_hash>
    addRepo(Repo(Regex(".*"), "SNAPSHOT", "https://my-repo.com/snapshots", true))
    // Add tasks which should be published
    publishTask("jar")
    publishTask("sourcesJar")
    publishTask("javadocJar")
}

publishing {
    publications.create<MavenPublication>("maven") {
        // configure the publication as defined previously.
        publishData.configurePublication(this)
    }

    repositories {
        maven {
            authentication {
                // Auth for the repository
                // ....
            }

            name = "MyRepo"
            // Get the detected repository from the publish data
            url = uri(publishData.getRepository())
        }
    }
    // For gitlab
    publications.create<MavenPublication>("maven") {
        // configure the publication as defined previously.
        publishData.configurePublication(this)
    }

    repositories {
        maven {
            credentials(HttpHeaderCredentials::class) {
                name = "Job-Token"
                value = System.getenv("CI_JOB_TOKEN")
            }
            authentication {
                create("header", HttpHeaderAuthentication::class)
            }


            name = "Gitlab"
            // Get the detected repository from the publish data
            url = uri(publishData.getRepository())
        }
    }
}
```
## Manuall Repositories

When defining your own repositories, make sure to define them in the correct order. The first applicable repository will be chosen. So if you register a wildcard repo first, it will always chose the wildcard repository.

## Usage of getVersion

If you want to use publishData.getVersion() in your code to get a version, make sure that the publishData configuration section is located before that part referencing it.
