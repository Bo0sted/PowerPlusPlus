plugins {
    id("powerpp.panel.module")
}

android {
    namespace = "com.bo0sted.powerplusplus.module.screenshot"
}

dependencies {
    implementation(project(":core:overlay"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
