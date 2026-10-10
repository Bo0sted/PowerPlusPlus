plugins {
    id("powerpp.android.library")
    id("powerpp.android.compose")
}

android {
    namespace = "com.bo0sted.powerplusplus.media"
}

dependencies {
    implementation(project(":core:capture"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
