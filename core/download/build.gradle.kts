plugins {
    id("powerpp.android.library")
}

android {
    namespace = "com.bo0sted.powerplusplus.download"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
