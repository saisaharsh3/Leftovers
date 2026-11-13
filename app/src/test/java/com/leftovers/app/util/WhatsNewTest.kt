package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Highlights opens once per feature version: not again for its pre-releases or Dev builds. */
class WhatsNewTest {
    @Test fun aFeatureVersionCoversItsPreReleasesAndDevBuilds() {
        assertEquals("1.10", WhatsNew.release("1.10.0"))
        assertEquals("1.10", WhatsNew.release("1.10.0-rc2"))
        assertEquals("1.10", WhatsNew.release("1.10.0-rc2-dev.185"))
        assertEquals("1.10", WhatsNew.release("1.10.3"))
        assertEquals("1.11", WhatsNew.release("1.11.0-dev.190"))
    }
}
