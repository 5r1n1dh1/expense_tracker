plugins {
    alias(libs.plugins.android.application)
    // No kotlin-android plugin: AGP 9.x has built-in Kotlin support.
    // Only the Compose compiler and Serialization plugins are needed separately.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
}