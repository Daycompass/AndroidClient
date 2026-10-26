package org.daycompass.android.pairing

import org.daycompass.android.api.TestValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingLinkTest {
    private val code = TestValues.pairingCode().replace("-", "")

    @Test
    fun validLinkParses() {
        val raw = link(server = "https://example.org", code = code)

        val result = parsePairingLink(raw) as PairingLinkResult.Valid

        assertEquals("example.org", result.serverUrl.host)
        assertEquals(code, result.code)
    }

    @Test
    fun unknownExtraParamIsIgnored() {
        val raw = link(server = "https://example.org", code = code) + "&future=1"

        assertTrue(parsePairingLink(raw) is PairingLinkResult.Valid)
    }

    @Test
    fun missingVersionIsUnsupported() {
        val raw = "daycompass://pair?server=${enc("https://example.org")}&code=$code"

        assertEquals(PairingLinkResult.Invalid.UnsupportedVersion, parsePairingLink(raw))
    }

    @Test
    fun wrongVersionIsUnsupported() {
        val raw = link(server = "https://example.org", code = code, version = "2")

        assertEquals(PairingLinkResult.Invalid.UnsupportedVersion, parsePairingLink(raw))
    }

    @Test
    fun missingCodeIsMissingCode() {
        val raw = "daycompass://pair?v=1&server=${enc("https://example.org")}"

        assertEquals(PairingLinkResult.Invalid.MissingCode, parsePairingLink(raw))
    }

    @Test
    fun blankCodeIsMissingCode() {
        val raw = "daycompass://pair?v=1&server=${enc("https://example.org")}&code="

        assertEquals(PairingLinkResult.Invalid.MissingCode, parsePairingLink(raw))
    }

    @Test
    fun serverWithPathIsMalformed() {
        val raw = link(server = "https://example.org/dashboard", code = code)

        assertEquals(PairingLinkResult.Invalid.MalformedServer, parsePairingLink(raw))
    }

    @Test
    fun serverWithUserinfoIsMalformed() {
        val raw = link(server = "https://alice:hunter2@example.org", code = code)

        assertEquals(PairingLinkResult.Invalid.MalformedServer, parsePairingLink(raw))
    }

    @Test
    fun malformedPercentEncodingInServerIsMalformed() {
        val raw = "daycompass://pair?v=1&server=https%3A%2F%2Fexample.org%&code=$code"

        assertEquals(PairingLinkResult.Invalid.MalformedServer, parsePairingLink(raw))
    }

    @Test
    fun percentEncodedIpv6ServerWithPortParses() {
        val raw = link(server = "https://[2001:db8::1]:8080", code = code)

        val result = parsePairingLink(raw) as PairingLinkResult.Valid

        assertEquals(8080, result.serverUrl.port)
        assertTrue(result.serverUrl.host.contains("2001:db8::1"))
    }

    @Test
    fun wrongSchemeIsNotAPairingLink() {
        assertEquals(
            PairingLinkResult.Invalid.NotAPairingLink,
            parsePairingLink("https://example.org/pair?v=1&server=x&code=$code"),
        )
    }

    @Test
    fun wrongHostIsNotAPairingLink() {
        val raw = "daycompass://unpair?v=1&server=${enc("https://example.org")}&code=$code"

        assertEquals(PairingLinkResult.Invalid.NotAPairingLink, parsePairingLink(raw))
    }

    @Test
    fun validHttpsLinkNeedsConfirmationNotAnImmediatePairAttempt() {
        val raw = link(server = "https://example.org", code = code)

        val outcome = interpretPairingLink(raw)

        // The return type itself proves this: NeedsConfirmation carries no pairing outcome,
        // only data for the UI to show before anyone has confirmed anything.
        assertTrue(outcome is PairingLinkOutcome.NeedsConfirmation)
        assertEquals("example.org", (outcome as PairingLinkOutcome.NeedsConfirmation).serverUrl.host)
    }

    @Test
    fun httpLinkIsRejectedKeepingTheHttpsOnlyPolicy() {
        val raw = link(server = "http://example.org", code = code)

        val outcome = interpretPairingLink(raw) as PairingLinkOutcome.Rejected

        assertEquals(PairingError.NotHttps, outcome.error)
    }

    @Test
    fun unsupportedVersionLinkIsRejectedWithItsOwnMessage() {
        val raw = link(server = "https://example.org", code = code, version = "2")

        val outcome = interpretPairingLink(raw) as PairingLinkOutcome.Rejected

        assertEquals(PairingError.LinkUnsupportedVersion, outcome.error)
    }

    @Test
    fun notAPairingLinkIsRejectedAsGenerallyInvalid() {
        val outcome = interpretPairingLink("https://example.org/") as PairingLinkOutcome.Rejected

        assertEquals(PairingError.LinkInvalid, outcome.error)
    }

    private fun link(
        server: String,
        code: String,
        version: String = "1",
    ): String = "daycompass://pair?v=$version&server=${enc(server)}&code=$code"

    /** A minimal RFC 3986 percent-encoder for building test fixtures; ASCII input only. */
    private fun enc(value: String): String =
        value
            .map { c -> if (c.isLetterOrDigit() || c in "-._~") c.toString() else "%%%02X".format(c.code) }
            .joinToString("")
}
