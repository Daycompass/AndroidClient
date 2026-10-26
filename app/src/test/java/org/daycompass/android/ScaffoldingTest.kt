package org.daycompass.android

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaffoldingTest {
    @Test
    fun mainActivityLivesInTheApplicationPackage() {
        assertEquals("org.daycompass.android.MainActivity", MainActivity::class.java.name)
    }
}
