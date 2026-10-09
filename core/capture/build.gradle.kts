plugins {
    id("powerpp.android.library")
}

android {
    namespace = "com.bo0sted.powerplusplus.capture"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
