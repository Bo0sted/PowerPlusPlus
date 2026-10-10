plugins {
    id("powerpp.panel.module")
}

android {
    namespace = "com.bo0sted.powerplusplus.module.mediaextractor"
}

dependencies {
    implementation(project(":core:capture"))
    implementation(project(":core:media"))
    implementation(project(":core:overlay"))
    implementation(project(":core:ui"))
}
