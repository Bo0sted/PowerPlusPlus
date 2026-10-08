pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PowerPlusPlus"

include(":app")
include(":core:ui")
include(":core:module")
include(":core:data")
include(":core:overlay")
include(":module:powermenu")
