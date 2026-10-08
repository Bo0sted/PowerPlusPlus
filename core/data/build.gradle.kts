plugins {
    id("powerpp.android.library")
    id("powerpp.serialization")
    id("powerpp.hilt")
}

android {
    namespace = "com.bo0sted.powerplusplus.data"
}

dependencies {
    api(libs.androidx.datastore)
    api(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
