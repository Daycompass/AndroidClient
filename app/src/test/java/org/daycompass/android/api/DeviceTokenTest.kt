package org.daycompass.android.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceTokenTest {
    @Test
    fun toStringNeverRevealsTheToken() {
        val secret = TestValues.tokenValue()
        val token = DeviceToken(secret)

        assertEquals("DeviceToken(***)", token.toString())
        assertFalse(token.toString().contains(secret.removePrefix("dcd_")))
        assertFalse("$token".contains(secret))
    }

    @Test
    fun authorizationHeaderUsesTheBearerScheme() {
        val secret = TestValues.tokenValue()

        assertEquals("Bearer $secret", DeviceToken(secret).authorizationHeaderValue())
    }

    @Test
    fun rejectsValuesWithoutTheDevicePrefixWithoutEchoingThem() {
        val notAToken = TestValues.hex()

        val error = assertThrows(IllegalArgumentException::class.java) { DeviceToken(notAToken) }
        assertFalse(error.message.orEmpty().contains(notAToken))
        assertThrows(IllegalArgumentException::class.java) { DeviceToken("dcd_") }
    }
}
