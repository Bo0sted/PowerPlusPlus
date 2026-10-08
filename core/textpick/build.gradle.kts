plugins {
    id("powerpp.android.library")
    id("powerpp.android.compose")
}

android {
    namespace = "com.bo0sted.powerplusplus.textpick"
}

dependencies {
    implementation(project(":core:overlay"))
    implementation(project(":core:ui"))
    testImplementation(libs.junit)
}
