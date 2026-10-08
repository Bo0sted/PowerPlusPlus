import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

pluginManager.withPlugin("com.android.application") {
    extensions.configure<ApplicationExtension> { buildFeatures { compose = true } }
}
pluginManager.withPlugin("com.android.library") {
    extensions.configure<LibraryExtension> { buildFeatures { compose = true } }
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

dependencies {
    val bom = platform(lib("compose-bom"))
    "implementation"(bom)
    "implementation"(lib("compose-ui"))
    "implementation"(lib("compose-foundation"))
    "implementation"(lib("compose-material3"))
    "implementation"(lib("compose-material-icons-extended"))
    "implementation"(lib("compose-ui-tooling-preview"))
    "debugImplementation"(lib("compose-ui-tooling"))
}
