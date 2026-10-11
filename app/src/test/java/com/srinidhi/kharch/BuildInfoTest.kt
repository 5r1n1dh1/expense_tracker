package com.srinidhi.kharch

import com.srinidhi.kharch.ui.BuildInfo
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Skeleton smoke tests. Real suites land in Phase 1 onward (§13).
 */
class BuildInfoTest {
    @Test
    fun `version name is non-blank`() {
        assertTrue(BuildInfo.VERSION_NAME.isNotBlank())
    }

    @Test
    fun `version code is positive`() {
        assertTrue(BuildInfo.VERSION_CODE > 0)
    }
}
