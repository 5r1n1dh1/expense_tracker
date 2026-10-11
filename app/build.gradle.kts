import java.util.Properties

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

/**
 * Release signing (Phase 11). The keystore is NEVER in the repository — CI decodes it from
 * the KEYSTORE_BASE64 secret into a temp dir and points these properties at it. When the
 * properties are absent (local builds, pull-request builds from forks) the release variant
 * simply falls back to unsigned, which is intentional: a PR must never be able to produce a
 * signed artifact.
 */
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreProps.getProperty("storeFile")?.let { file(it).exists() } == true

android {
    namespace = "com.srinidhi.kharch"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.srinidhi.kharch"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.srinidhi.kharch.HiltTestRunner"
        vectorDrawables.useSupportLibrary = true

        // D5: INR is the only currency in v1, but amounts are stored as paise and the code
        // carries an ISO-4217 field, so adding a currency later needs no data migration.
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            // R8 + resource shrinking (P8 / §18.10). Room entities, Firestore DTOs, Hilt and
            // Glance receivers must survive — see proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // With built-in Kotlin, jvmTarget defaults to compileOptions.targetCompatibility (17),
    // so we don't set it explicitly. We only add opt-in compiler flags here.
    kotlin {
        compilerOptions {
            freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true   // Robolectric-capable (§13)
            isReturnDefaultValues = true
        }
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        animationsDisabled = false            // exercise the motion system (§7.4)
        retry {
            maxRetries = 2                     // CI-flakiness mitigation (§16)
            maxFailures = 3
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "DebugProbesKt.bin"
        }
    }

    // P9 (§18.2): every schema version exports its schema JSON. Without exported schemas
    // MigrationTestHelper cannot test anything, and migration bugs are the single biggest
    // real-world data-loss risk for an offline-first money app.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.generateKotlin", "true")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)

    // Compose — BOM keeps every Compose artifact binary-compatible
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.work.runtime.ktx)

    // Credential Manager (D3: modern Google sign-in, no legacy play-services-auth UI)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.play.services.auth)

    // Firebase — Auth + Firestore ONLY (Spark / free tier, D2)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.common)
    // P1: no crashlytics / analytics / performance — nothing leaves the device.

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.kotlinx.coroutines.android)

    // --- test ---
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.kotlinx.coroutines.debug)
    testImplementation(libs.androidx.test.core.ktx)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("$rootDir/config/detekt/baseline.xml")
    source.setFrom("src/main/java", "src/test/java", "src/androidTest/java")
}

// §18.3 — P8 / P1: user financial data must never reach Logcat.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
    }
}

tasks.withType<Test>().configureEach {
    // Screenshot/Paparazzi tests capture to build/; keep them on disk for CI artifacts.
    systemProperty("robolectric.graphicsMode", "NATIVE")
}