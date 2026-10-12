plugins {
    id("powerpp.android.library")
    id("powerpp.android.compose")
    id("powerpp.serialization")
    id("powerpp.hilt")
}

android {
    namespace = "com.bo0sted.powerplusplus.assistant"
}

dependencies {
    api(project(":core:whisper"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
