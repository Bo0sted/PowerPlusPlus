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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
