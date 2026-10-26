package org.daycompass.android.pairing

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream

/**
 * `daycompass://pair?v=1&server=<percent-encoded origin>&code=<code>`. See the server's
 * docs/wire-format.md, "Pairing links and QR codes".
 */
sealed interface PairingLinkResult {
    /** [code] is sent unchanged, exactly as it appeared in the link, to `POST /api/v1/pair`. */
    data class Valid(
        val serverUrl: HttpUrl,
        val code: String,
    ) : PairingLinkResult {
        override fun toString(): String = "Valid(serverUrl=$serverUrl, code=***)"
    }

    sealed interface Invalid : PairingLinkResult {
        /** Not a `daycompass://pair` link at all: wrong scheme, wrong host, or a path. */
        data object NotAPairingLink : Invalid

        /** `v` is missing or not exactly `"1"`. */
        data object UnsupportedVersion : Invalid

        /** `code` is missing or blank. */
        data object MissingCode : Invalid

        /** `server` is missing, not percent-decodable, or not a bare http(s) origin. */
        data object MalformedServer : Invalid
    }
}

/**
 * What happens when a pairing link is scanned or opened. Never pairs by itself: pairing only
 * happens when the person later confirms via [SetupViewModel.confirmLinkPairing].
 */
sealed interface PairingLinkOutcome {
    data class NeedsConfirmation(
        val serverUrl: HttpUrl,
        val code: String,
    ) : PairingLinkOutcome {
        override fun toString(): String = "NeedsConfirmation(serverUrl=$serverUrl, code=***)"
    }

    data class Rejected(
        val error: PairingError,
    ) : PairingLinkOutcome
}

private const val SCHEME_PREFIX = "daycompass://"
private const val PAIR_HOST = "pair"
private const val LINK_VERSION = "1"

fun parsePairingLink(raw: String): PairingLinkResult {
    val query = matchPairLinkQuery(raw) ?: return PairingLinkResult.Invalid.NotAPairingLink
    val params = parseQueryParams(query)
    return when {
        params["v"] != LINK_VERSION -> {
            PairingLinkResult.Invalid.UnsupportedVersion
        }

        params["code"].isNullOrBlank() -> {
            PairingLinkResult.Invalid.MissingCode
        }

        else -> {
            params["server"]
                ?.let(::parsePairingServerOrigin)
                ?.let { PairingLinkResult.Valid(it, params.getValue("code")) }
                ?: PairingLinkResult.Invalid.MalformedServer
        }
    }
}

/** The link's raw (undecoded) query string, or `null` if this isn't a `daycompass://pair` link. */
private fun matchPairLinkQuery(raw: String): String? {
    if (!raw.startsWith(SCHEME_PREFIX, ignoreCase = true)) return null
    val afterScheme = raw.substring(SCHEME_PREFIX.length)
    val authorityEnd =
        afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }.takeIf { it >= 0 }
            ?: afterScheme.length
    val host = afterScheme.substring(0, authorityEnd)
    val rest = afterScheme.substring(authorityEnd)
    // The server's encoder never emits a path; anything but an empty rest, a query, or a
    // fragment here is not a link shape this client understands.
    val isHostMatch = host.equals(PAIR_HOST, ignoreCase = true)
    val hasNoPath = rest.isEmpty() || rest[0] == '?' || rest[0] == '#'
    return if (isHostMatch && hasNoPath) rest.substringAfter('?', "").substringBefore('#') else null
}

fun interpretPairingLink(raw: String): PairingLinkOutcome =
    when (val result = parsePairingLink(raw)) {
        is PairingLinkResult.Valid -> {
            if (result.serverUrl.isHttps) {
                PairingLinkOutcome.NeedsConfirmation(result.serverUrl, result.code)
            } else {
                // This app keeps its HTTPS-only fixed design decision even though the wire
                // format allows http for other platforms' LAN installs.
                PairingLinkOutcome.Rejected(PairingError.NotHttps)
            }
        }

        PairingLinkResult.Invalid.UnsupportedVersion -> {
            PairingLinkOutcome.Rejected(PairingError.LinkUnsupportedVersion)
        }

        PairingLinkResult.Invalid.NotAPairingLink,
        PairingLinkResult.Invalid.MissingCode,
        PairingLinkResult.Invalid.MalformedServer,
        -> {
            PairingLinkOutcome.Rejected(PairingError.LinkInvalid)
        }
    }

/** Splits the raw (undecoded) query on `&`, then on the first `=`; later keys win. */
private fun parseQueryParams(query: String): Map<String, String> =
    query
        .split('&')
        .filter { it.isNotEmpty() }
        .mapNotNull { pair ->
            val eq = pair.indexOf('=')
            val rawKey = if (eq >= 0) pair.substring(0, eq) else pair
            val rawValue = if (eq >= 0) pair.substring(eq + 1) else ""
            val key = percentDecode(rawKey) ?: return@mapNotNull null
            val value = percentDecode(rawValue) ?: return@mapNotNull null
            key to value
        }.toMap()

/** Every character outside RFC 3986's unreserved set is percent-encoded in a pairing link. */
private fun percentDecode(value: String): String? {
    val bytes = ByteArrayOutputStream(value.length)
    var i = 0
    var valid = true
    while (i < value.length && valid) {
        val c = value[i]
        when {
            c == '%' && i + PERCENT_TRIPLET_LENGTH <= value.length -> {
                val byte = value.substring(i + 1, i + PERCENT_TRIPLET_LENGTH).toIntOrNull(HEX_RADIX)
                if (byte == null) {
                    valid = false
                } else {
                    bytes.write(byte)
                    i += PERCENT_TRIPLET_LENGTH
                }
            }

            c != '%' && c.code <= ASCII_MAX -> {
                bytes.write(c.code)
                i += 1
            }

            else -> {
                valid = false
            }
        }
    }
    return if (valid) runCatching { bytes.toByteArray().toString(Charsets.UTF_8) }.getOrNull() else null
}

/** The `server` parameter: an http(s) origin with a host and optional port, nothing else. */
private fun parsePairingServerOrigin(decoded: String): HttpUrl? =
    decoded
        .toHttpUrlOrNull()
        ?.takeIf { it.username.isEmpty() && it.password.isEmpty() }
        ?.takeIf { it.encodedPath == "/" }
        ?.takeIf { it.query == null && it.fragment == null }

private const val ASCII_MAX = 0x7F

/** `%` plus two hex digits. */
private const val PERCENT_TRIPLET_LENGTH = 3
private const val HEX_RADIX = 16
