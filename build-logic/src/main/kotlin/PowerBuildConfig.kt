import org.gradle.api.JavaVersion

/** SDK and JVM levels shared by every Power++ module. */
object PowerBuildConfig {
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 37
    const val MIN_SDK = 31
    val JAVA_VERSION = JavaVersion.VERSION_21
}
