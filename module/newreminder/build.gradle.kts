plugins {
    id("powerpp.panel.module")
}

android {
    namespace = "com.bo0sted.powerplusplus.module.newreminder"
}

dependencies {
    implementation(project(":command:reminders"))
}
