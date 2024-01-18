package de.chojo

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

@Suppress("MemberVisibilityCanBePrivate", "unused")
open class PublishDataExtension(private val project: Project) {
    var versionCleaner: Regex = Regex("-SNAPSHOT|-DEV")

    @Input
    @Optional
    var hashLength: Int = 7

    @Input
    @Optional
    var publishingVersion: String? = null
    var repos: MutableSet<Repo> = mutableSetOf()
    var components: MutableSet<String> = mutableSetOf()
    var tasks: MutableSet<String> = mutableSetOf()
    var repo: Repo? = null
    var addBuildData = false
    var additionalData: Map<String, String> = emptyMap()

    /**
     * Registers a repository.
     *
     * Order matters. The first registered repository has the highest priority.
     * The first repo which matches will be the one to be used.
     */
    fun addRepo(repo: Repo) {
        project.logger.debug(
            "Registered repository {} with identifier \"{}\" matching \"{}\"",
            repo.url,
            repo.marker,
            repo.identifier
        )
        if (repos.isEmpty() && repo.type != Repo.Type.STABLE) {
            project.logger.warn("Non stable repository registered as first. Make sure to register repos in the correct order.")
            project.logger.warn("The first matching repository will be chosen. It should be a stable repository")
        }
        repos.add(repo)
    }

    fun addBuildData(additionalData: Map<String, String> = emptyMap()) {
        addBuildData = true
        this.additionalData = additionalData
    }

    fun isBuildDataActive(): Boolean {
        return addBuildData
    }

    /**
     * Configures the repositories to use the gitlab repositories as defined in [Repo.master], [Repo.main] and [Repo.snapshot]
     */
    fun useGitlabReposForProject(projectId: String, gitlabUrl: String = "https://gitlab.com/") {
        addRepo(Repo.main("", "${gitlabUrl}api/v4/projects/$projectId/packages/maven", false))
        addRepo(Repo.master("", "${gitlabUrl}api/v4/projects/$projectId/packages/maven", false))
        addRepo(Repo.snapshot("SNAPSHOT", "${gitlabUrl}api/v4/projects/$projectId/packages/maven", true))
    }

    /**
     * Configures the repositories to use the eldonexus repositories as defined in [Repo.master], [Repo.main], [Repo.dev] and [Repo.snapshot]
     */
    fun useEldoNexusRepos(dev: Boolean = true) {
        addRepo(Repo.main("", "https://eldonexus.de/repository/maven-releases/", false))
        addRepo(Repo.master("", "https://eldonexus.de/repository/maven-releases/", false))
        if (dev) addRepo(Repo.dev("DEV", "https://eldonexus.de/repository/maven-dev/", true))
        addRepo(Repo.snapshot("SNAPSHOT", "https://eldonexus.de/repository/maven-snapshots/", true))
    }

    /**
     * Configures the repositories to use the internal eldonexus repositories as defined in [Repo.master], [Repo.main], [Repo.dev] and [Repo.snapshot]
     */
    fun useInternalEldoNexusRepos() {
        addRepo(Repo.main("", "https://eldonexus.de/repository/maven-releases-internal/", false))
        addRepo(Repo.master("", "https://eldonexus.de/repository/maven-releases-internal/", false))
        addRepo(Repo.dev("DEV", "https://eldonexus.de/repository/maven-dev-internal/", true))
        addRepo(Repo.snapshot("SNAPSHOT", "https://eldonexus.de/repository/maven-snapshots-internal/", true))
    }

    /**
     * Adds a task by name to get published
     */
    fun publishTask(task: String) {
        project.logger.debug("Registered task {} for publishing", task)
        tasks.add(task)
    }

    /**
     * Adds a task to get published
     */
    fun publishTask(task: Task) {
        publishTask(task.name)
    }

    /**
     * Adds a component to get published
     */
    fun publishComponent(component: String) {
        project.logger.debug("Registered component {} for publishing", component)
        components.add(component)
    }

    private fun getReleaseType(): Repo? {
        if (repo != null) {
            return repo
        }
        val branch = getBranch()
        val first = repos.firstOrNull { r -> r.isRepo(branch) }
        println(if (first == null) "Could not detect release type" else "Detected release of ${first.identifier}")
        return first
    }

    /**
     * Configure a [MavenPublication]. Adds the [tasks] and [components] and calls [MavenPublication.setVersion],
     * [MavenPublication.setArtifactId] and [MavenPublication.setGroupId]
     */
    fun configurePublication(publication: MavenPublication) {
        for (component in components) {
            publication.from(project.components.getByName(component))
        }
        for (task in tasks) {
            publication.artifact(project.tasks.getByName(task))
        }
        publication.version = getVersion()
        publication.artifactId = getProjectName()
        publication.groupId = getGroupId()
    }

    private fun getGroupId(): String {
        var groupId = (project.rootProject.group as String)
        if (groupId.isBlank()) {
            groupId = (project.group as String)
        }
        return groupId
    }

    internal fun getProjectName(): String {
        var name = (project.name as String)
        if (name.isBlank()) {
            name = (project.rootProject.name as String)
        }
        return name.lowercase()
    }

    private fun getGithubCommitHash(): String? =
        System.getenv("GITHUB_SHA")?.substring(0, hashLength)

    private fun getGitlabCommitHash(): String? =
        System.getenv("CI_COMMIT_SHA")?.substring(0, hashLength)

    /**
     * Get the commit hash.
     */
    fun getCommitHash(): String {
        return getGithubCommitHash() ?: getGitlabCommitHash() ?: determineLocalCommitHash()
    }

    /**
     * Get the version with the optional [Repo.marker] without the commit hash
     */
    fun getVersion(): String = getVersion(false)

    /**
     * Get the version with the optional [Repo.marker]. Will append the commit hash when [Repo.addCommit] and [appendCommit] is set to true
     */
    fun getVersion(appendCommit: Boolean): String {
        return getReleaseType()?.append(getVersionString(), getCommitHash(), appendCommit) ?: "undefined"
    }

    private fun getVersionString(): String {
        var version = publishingVersion ?: project.version as String
        if (version.isBlank() || version == "unspecified") {
            version = (project.rootProject.version as String)
        }
        return version.replace(versionCleaner, "")
    }

    /**
     * Get the [Repo.url]
     */
    fun getRepository(): String = getReleaseType()?.url ?: ""

    private fun determineLocalCommitHash(): String {
        val localBranch = determineLocalBranchInternal()
        project.logger.warn("Building on branch $localBranch")
        if (localBranch == null) return "none"
        val file = project.rootProject.file(".git/refs/heads/${localBranch}")
        if (!file.exists()) return "undefined"
        val hash = file.useLines { it.firstOrNull() }
        return hash?.substring(0, hashLength) ?: "undefined"
    }

    /**
     * Get the current branch which is determined by GitHub or the local git repository
     */
    fun getBranch(): String = getGithubBranch() ?: getGitlabBranch() ?: determineLocalBranch()

    private fun getGithubBranch(): String? {
        return System.getenv("GITHUB_REF")?.replace("refs/heads/", "")
    }

    private fun getGitlabBranch(): String? {
        return System.getenv("CI_COMMIT_BRANCH")?.replace("refs/heads/", "")
    }

    private fun determineLocalBranch(): String {
        if (!isPublicBuild()) {
            project.logger.warn("Local build detected. Set the env variable PUBLIC_BUILD=true to build non local builds")
            return "local"
        }
        return determineLocalBranchInternal() ?: "none"
    }

    private fun determineLocalBranchInternal(): String? {
        val file = project.rootProject.file(".git/HEAD")
        if (!file.exists()) return null
        val branch = file.useLines { it.firstOrNull() }
        return branch?.replace("ref: refs/heads/", "") ?: "local"
    }

    /**
     * Check if the build is a public build.
     */
    fun isPublicBuild(): Boolean {
        return (System.getenv("PUBLIC_BUILD") ?: "false").contentEquals("true")
    }

    fun getBuildType(): String {
        return when {
            System.getenv("BUILD_TYPE") != null -> {
                return System.getenv("BUILD_TYPE")
            }

            System.getenv("PATREON")?.equals("true", true) == true -> {
                "PATREON"
            }

            isPublicBuild() || getGitlabBranch() != null || getGithubBranch() != null -> {
                "PUBLIC"
            }

            else -> "LOCAL"
        }
    }
}
