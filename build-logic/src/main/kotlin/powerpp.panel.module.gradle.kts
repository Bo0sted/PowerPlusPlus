/** Everything a `:module:*` feature needs: it implements a PanelModule and contributes it via Hilt. */
plugins {
    id("powerpp.android.library")
    id("powerpp.android.compose")
    id("powerpp.hilt")
}

dependencies {
    "implementation"(project(":core:module"))
}
