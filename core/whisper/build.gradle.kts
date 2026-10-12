plugins {
    id("powerpp.android.library")
    id("powerpp.hilt")
}

android {
    namespace = "com.bo0sted.powerplusplus.whisper"
    ndkVersion = PowerBuildConfig.NDK_VERSION

    defaultConfig {
        // x86 (32-bit) has no Android 12+ phones; the speech engine reports it as unsupported there.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        externalNativeBuild {
            // Several native libraries share the C++ runtime, so it must be the shared one.
            cmake { arguments += "-DANDROID_STL=c++_shared" }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = PowerBuildConfig.CMAKE_VERSION
        }
    }
}

dependencies {
    api(project(":core:download"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
