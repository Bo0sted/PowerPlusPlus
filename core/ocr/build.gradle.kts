plugins {
    id("powerpp.android.library")
    id("powerpp.hilt")
}

android {
    namespace = "com.bo0sted.powerplusplus.ocr"
    buildFeatures { buildConfig = true }
    defaultConfig {
        // The engine downloaded at runtime must match the ORT Java classes and JNI glue bundled here.
        buildConfigField("String", "ORT_VERSION", "\"${libs.versions.onnxruntime.get()}\"")
    }
}

dependencies {
    // Java API + the small JNI glue only: the app's packaging excludes libonnxruntime.so (the engine
    // itself), which is downloaded instead. See OrtEngine.
    api(libs.onnxruntime.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
