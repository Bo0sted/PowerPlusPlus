plugins {
    id("powerpp.assistant.command")
}

android {
    namespace = "com.bo0sted.powerplusplus.command.reminders"
}

dependencies {
    implementation(project(":core:ntfy"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
