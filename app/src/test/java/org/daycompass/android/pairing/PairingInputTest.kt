package org.daycompass.android.pairing

import org.daycompass.android.api.ApiResult
import org.daycompass.android.api.TestValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class PairingInputTest {
    @Test
    fun bareHostGetsHttpsAndATrailingSlash() {
        assertEquals("https://example.org/", parseServerUrl("  example.org ").getOrThrow().toString())
        assertEquals("https://example.org:8443/", parseServerUrl("example.org:8443").getOrThrow().toString())
    }

    @Test
    fun pathPrefixIsKeptAsADirectory() {
        assertEquals(
            "https://example.org/daycompass/",
            parseServerUrl("https://example.org/daycompass").getOrThrow().toString(),
        )
        assertEquals(
            "https://example.org/daycompass/",
            parseServerUrl("https://example.org/daycompass/").getOrThrow().toString(),
        )
    }

    @Test
    fun plainHttpIsRefusedNotUpgraded() {
        val problem = (parseServerUrl("http://example.org").exceptionOrNull() as ServerUrlException).problem

        assertEquals(ServerUrlProblem.NotHttps, problem)
    }

    @Test
    fun unusableUrlsAreInvalid() {
        listOf("", "   ", "https://", "ftp://example.org", "https://alice:pw@example.org", "https://example.org/?a=b")
            .forEach { input ->
                val problem = (parseServerUrl(input).exceptionOrNull() as? ServerUrlException)?.problem
                assertEquals(input, ServerUrlProblem.Invalid, problem)
            }
    }

    @Test
    fun deviceNameFollowsTheServerPolicy() {
        assertTrue(isValidDeviceName("Pixel 8"))
        assertTrue(isValidDeviceName("  Pixel 8  "))
        assertTrue(isValidDeviceName("x".repeat(64)))
        assertTrue(isValidDeviceName("📱".repeat(64)))
        assertFalse(isValidDeviceName(""))
        assertFalse(isValidDeviceName("   "))
        assertFalse(isValidDeviceName("x".repeat(65)))
        assertFalse(isValidDeviceName("Pixel\u00078"))
        assertFalse(isValidDeviceName("Pixel ‮8"))
        assertFalse(isValidDeviceName("Pixel ⁨8"))
    }

    @Test
    fun defaultDeviceNameIsTheModelCleanedUp() {
        assertEquals("Pixel 8", defaultDeviceName(" Pixel 8 "))
        assertEquals("Pixel8", defaultDeviceName("Pixel\u00008"))
        assertEquals("x".repeat(64), defaultDeviceName("x".repeat(80)))
        assertEquals("Android device", defaultDeviceName(null))
        assertEquals("Android device", defaultDeviceName("  "))
        assertTrue(isValidDeviceName(defaultDeviceName("‮".repeat(3) + "y".repeat(70))))
    }

    @Test
    fun serverAnswersMapToFormErrors() {
        assertNull(ApiResult.Success(Unit).toPairingError())
        assertEquals(PairingError.InvalidCode, ApiResult.ApiError(401, "invalid_pairing_code").toPairingError())
        assertEquals(PairingError.DeviceLimitReached, ApiResult.ApiError(409, "device_limit_reached").toPairingError())
        assertEquals(PairingError.InvalidDeviceName, ApiResult.ApiError(400, "invalid_device_name").toPairingError())
        assertEquals(PairingError.RateLimited(90), ApiResult.RateLimited(90).toPairingError())
        assertEquals(PairingError.Redirected, ApiResult.ApiError(301, null).toPairingError())
        assertEquals(PairingError.NotDaycompass, ApiResult.ApiError(404, "not_found").toPairingError())
        assertEquals(PairingError.NotDaycompass, ApiResult.ApiError(400, null).toPairingError())
        assertEquals(PairingError.ServerError, ApiResult.ApiError(502, null).toPairingError())
        assertEquals(PairingError.Unexpected, ApiResult.ApiError(400, "invalid_platform").toPairingError())
        assertEquals(PairingError.NotDaycompass, ApiResult.MalformedResponse.toPairingError())
    }

    @Test
    fun networkFailuresDistinguishCertificatesFromReachability() {
        assertEquals(
            PairingError.UntrustedCertificate,
            ApiResult.NetworkError(SSLHandshakeException("x")).toPairingError(),
        )
        assertEquals(PairingError.Unreachable, ApiResult.NetworkError(UnknownHostException("x")).toPairingError())
        assertEquals(PairingError.Unreachable, ApiResult.NetworkError(ConnectException("x")).toPairingError())
    }

    @Test
    fun retryAfterRoundsUpToWholeMinutes() {
        assertNull(retryAfterMinutes(null))
        assertEquals(1, retryAfterMinutes(0))
        assertEquals(1, retryAfterMinutes(60))
        assertEquals(2, retryAfterMinutes(61))
    }

    @Test
    fun formNeverPrintsThePairingCode() {
        val code =
            org.daycompass.android.api.TestValues
                .pairingCode()

        assertFalse(PairingForm(pairingCode = code).toString().contains(code))
    }
}
