// Detekt is applied per-module (see app/build.gradle.kts), NOT here.
// Applying Detekt at the root triggers AGP root-project evaluation, which
// built-in Kotlin rejects ("Android Gradle Plugin has been applied at the root
// build file"). Single-module project, so per-module is all we need.
plugins {
    alias(libs.plugins.android.application) apply false
    // No kotlin-android plugin: AGP 9.x has built-in Kotlin support.
    // Only the Compose compiler and Serialization plugins are needed separately.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}