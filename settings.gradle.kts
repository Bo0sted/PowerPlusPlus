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
include(":core:textpick")
include(":core:download")
include(":core:ntfy")
include(":core:ocr")
include(":core:whisper")
include(":core:assistant")
include(":core:capture")
include(":core:selection")
include(":core:media")
include(":module:powermenu")
include(":module:screentext")
include(":module:screenshot")
include(":module:ocrtext")
include(":module:mediaextractor")
include(":module:newreminder")
include(":command:reminders")
