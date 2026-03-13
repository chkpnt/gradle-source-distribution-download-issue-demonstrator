pluginManagement {
    repositories { maven(url = "https://maven-mirror.internal:8443/gradle-plugins/") }
}

dependencyResolutionManagement {
    //repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    // Otherwise, the repos added by RewriteDependencyRepositoriesPlugin are used, see
    // https://github.com/openrewrite/rewrite-build-gradle-plugin/blob/main/src/main/java/org/openrewrite/gradle/RewriteDependencyRepositoriesPlugin.java
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven(url = "https://maven-mirror.internal:8443/maven-central/")

        // In case you want to build :rewrite-gradle
        maven(url = "https://maven-mirror.internal:8443/gradle-plugins/")
        maven(url = "https://maven-mirror.internal:8443/gradle-libs")
    }
}

rootProject.name = "rewrite"

// ---------------------------------------------------------------
// ------ Included Projects --------------------------------------
// ---------------------------------------------------------------

val allProjects = listOf(
    "rewrite-benchmarks",
    "rewrite-bom",
    "rewrite-core",
    //"rewrite-csharp",
    "rewrite-docker",
    "rewrite-gradle", // tests fail
    "rewrite-gradle-tooling-model:model",
    "rewrite-gradle-tooling-model:plugin",
    "rewrite-groovy",
    "rewrite-hcl",
    "rewrite-java",
    "rewrite-java-tck",
    "rewrite-java-test",
    "rewrite-java-lombok",
    "rewrite-java-21",
    "rewrite-java-25",
    "rewrite-javascript",
    "rewrite-json",
    "rewrite-kotlin",
    "rewrite-maven",
    "rewrite-properties",
    "rewrite-protobuf",
    "rewrite-python",
    "rewrite-test",
    "rewrite-toml",
    "rewrite-xml",
    "rewrite-yaml",
)

val includedProjects = file("IDE.properties").let {
    if (it.exists() && (System.getProperty("idea.active") != null || System.getProperty("idea.sync.active") != null)) {
        val props = java.util.Properties()
        it.reader().use { reader ->
            props.load(reader)
        }
        allProjects.intersect(props.keys)
    } else {
        allProjects
    }
}.toSet()

include(*allProjects.toTypedArray())

gradle.allprojects {
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            allProjects
                .minus(includedProjects)
                .minus(
                    arrayOf(
                        "rewrite-bom",
                        "rewrite-gradle-tooling-model:model",
                        "rewrite-gradle-tooling-model:plugin"
                    )
                )
                .forEach {
                    substitute(project(":$it"))
                        .using(module("org.openrewrite:$it:latest.integration"))
                }
        }
    }
}

if (System.getProperty("idea.active") == null &&
    System.getProperty("idea.sync.active") == null
) {
    include(
        "rewrite-java-8",
        "rewrite-java-11",
        "rewrite-java-17",
        "rewrite-java-21",
        "rewrite-java-25"
    )
}

// ---------------------------------------------------------------
// ------ Gradle Develocity Configuration ------------------------
// ---------------------------------------------------------------

plugins {
    id("com.gradle.develocity") version "latest.release"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "latest.release"
}

develocity {
    val isCiServer = System.getenv("CI")?.equals("true") ?: false
    server = "https://ge.openrewrite.org/"
    val accessKey = System.getenv("GRADLE_ENTERPRISE_ACCESS_KEY")
    val authenticated = !accessKey.isNullOrBlank()
    buildCache {
        remote(develocity.buildCache) {
            isEnabled = true
            isPush = isCiServer && authenticated
        }
    }

    buildScan {
        capture {
            fileFingerprints = true
        }
        publishing {
            onlyIf {
                authenticated
            }
        }

        uploadInBackground = !isCiServer
    }
}
