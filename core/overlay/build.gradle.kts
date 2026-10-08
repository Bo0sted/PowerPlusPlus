plugins {
    id("powerpp.android.library")
    id("powerpp.android.compose")
}

android {
    namespace = "com.bo0sted.powerplusplus.overlay"
}

dependencies {
    api(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
}
