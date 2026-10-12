plugins {
    id("powerpp.assistant.command")
}

android {
    namespace = "com.bo0sted.powerplusplus.command.reminders"
}

dependencies {
    implementation(project(":core:ntfy"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
