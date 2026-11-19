package org.daycompass.android.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLabelTest {
    @Test
    fun controlCharactersAreStripped() {
        assertEquals("FooBar", sanitizeLabel("Foo\u0000Bar"))
    }

    @Test
    fun bidiOverrideCharactersAreStripped() {
        assertEquals("FooBar", sanitizeLabel("Foo‮Bar"))
        assertEquals("FooBar", sanitizeLabel("Foo⁦Bar"))
    }

    @Test
    fun emojiAtTheCodePointBoundaryIsKeptWhole() {
        val emoji = "😀" // U+1F600, one code point as a surrogate pair
        val raw = "a".repeat(127) + emoji + "b".repeat(5)

        val result = sanitizeLabel(raw)

        assertEquals("a".repeat(127) + emoji, result)
        assertEquals(128, result!!.codePointCount(0, result.length))
    }

    @Test
    fun sanitizingToNothingReturnsNull() {
        assertNull(sanitizeLabel("\u0000\u0001  "))
        assertNull(sanitizeLabel(""))
    }

    @Test
    fun resolverCachesEvenAnUnresolvableLookup() {
        var calls = 0
        val resolver =
            AppLabelResolver {
                calls++
                null
            }

        assertNull(resolver.labelFor("org.example.missing"))
        assertNull(resolver.labelFor("org.example.missing"))

        assertEquals(1, calls)
    }

    @Test
    fun resolverSanitizesTheRawName() {
        val resolver = AppLabelResolver { "Foo\u0000Bar" }

        assertEquals("FooBar", resolver.labelFor("org.example.app"))
    }

    @Test
    fun resolverCachesPerPackage() {
        var calls = 0
        val resolver =
            AppLabelResolver { appId ->
                calls++
                appId
            }

        resolver.labelFor("org.example.a")
        resolver.labelFor("org.example.a")
        resolver.labelFor("org.example.b")

        assertEquals(2, calls)
    }
}
