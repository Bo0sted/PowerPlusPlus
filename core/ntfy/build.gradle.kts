plugins {
    id("powerpp.android.library")
    id("powerpp.serialization")
    id("powerpp.hilt")
}

android {
    namespace = "com.bo0sted.powerplusplus.ntfy"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
