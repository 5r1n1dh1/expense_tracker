package com.srinidhi.kharch

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test — proves the APK actually installs and runs on the emulator,
 * which is the whole point of building in CI (§C1).
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeInstrumentedTest {
    @Test
    fun applicationContext_isAvailable() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        assertEquals("com.srinidhi.kharch", context.packageName)
    }
}
