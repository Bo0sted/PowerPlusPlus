plugins {
    id("powerpp.panel.module")
}

android {
    namespace = "com.bo0sted.powerplusplus.module.screentext"
}

dependencies {
    implementation(project(":core:textpick"))
}
