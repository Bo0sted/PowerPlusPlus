plugins {
    id("powerpp.android.application")
    id("powerpp.android.compose")
    id("powerpp.hilt")
}

android {
    namespace = "com.bo0sted.powerplusplus"

    defaultConfig {
        applicationId = "com.bo0sted.powerplusplus"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:module"))
    implementation(project(":core:data"))
    implementation(project(":core:overlay"))

    // Panel modules. Adding one here is all it takes to make it available in the editor.
    implementation(project(":module:powermenu"))
    implementation(project(":module:screentext"))
    implementation(project(":module:screenshot"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
}
