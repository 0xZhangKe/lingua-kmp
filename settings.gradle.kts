pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "lingua-kmp"

include(":lingua-core")
if (!providers.gradleProperty("lingua.desktopOnly").map(String::toBoolean).getOrElse(false)) {
    include(":demo", ":demo:androidApp")
}
