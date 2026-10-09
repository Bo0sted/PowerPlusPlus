plugins {
    id("powerpp.panel.module")
}

android {
    namespace = "com.bo0sted.powerplusplus.module.ocrtext"
}

dependencies {
    implementation(project(":core:capture"))
    implementation(project(":core:ocr"))
    implementation(project(":core:overlay"))
    implementation(project(":core:selection"))
    implementation(project(":core:textpick"))
    implementation(project(":core:ui"))
    testImplementation(libs.junit)
}
