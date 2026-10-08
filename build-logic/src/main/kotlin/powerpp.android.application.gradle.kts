import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application")
}

extensions.configure<ApplicationExtension> {
    compileSdk = PowerBuildConfig.COMPILE_SDK
    defaultConfig {
        minSdk = PowerBuildConfig.MIN_SDK
        targetSdk = PowerBuildConfig.TARGET_SDK
    }
    compileOptions {
        sourceCompatibility = PowerBuildConfig.JAVA_VERSION
        targetCompatibility = PowerBuildConfig.JAVA_VERSION
    }
}
