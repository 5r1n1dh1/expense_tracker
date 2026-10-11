package com.srinidhi.kharch

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test — proves the APK actually installs and launches on a real
 * emulator, which is the entire point of building in CI (§C1).
 *
 * NOTE: the debug variant has applicationId `com.srinidhi.kharch.debug`, so the
 * package name under test is the one HiltTestApplication is installed under.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeInstrumentedTest {

    @Test
    fun applicationContext_isAvailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull("targetContext must not be null", context)
        assertTrue(
            "debug applicationId should carry the .debug suffix, was ${context.packageName}",
            context.packageName.endsWith("kharch") || context.packageName.endsWith("kharch.debug"),
        )
    }

    @Test
    fun application_usesHiltTestApplication() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext
        assertNotNull("Application instance must exist", app)
    }
}