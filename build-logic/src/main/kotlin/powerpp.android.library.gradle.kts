import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
}

extensions.configure<LibraryExtension> {
    compileSdk = PowerBuildConfig.COMPILE_SDK
    defaultConfig {
        minSdk = PowerBuildConfig.MIN_SDK
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = PowerBuildConfig.JAVA_VERSION
        targetCompatibility = PowerBuildConfig.JAVA_VERSION
    }
}
