/** Everything a `:command:*` needs: it implements an AssistantCommand and contributes it via Hilt. */
plugins {
    id("powerpp.android.library")
    id("powerpp.android.compose")
    id("powerpp.hilt")
}

dependencies {
    "implementation"(project(":core:assistant"))
}
