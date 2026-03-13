pluginManagement {
    repositories { maven(url = "https://maven-mirror.internal:8443/gradle-plugins/") }
}

dependencyResolutionManagement {
    repositories { maven(url = "https://maven-mirror.internal:8443/maven-central/") }
}

rootProject.name = "simple-project"
include("lib")
