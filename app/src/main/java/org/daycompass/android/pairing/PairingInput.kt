package org.daycompass.android.pairing

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Longest device name the server accepts, counted in characters after trimming. */
const val MAX_DEVICE_NAME_LENGTH = 64

/** Why a server URL as typed cannot be used. */
enum class ServerUrlProblem { Invalid, NotHttps }

/**
 * Turns what the user typed into the server's base URL. A bare host gets `https://`; plain
 * `http://` is refused rather than upgraded, so the user sees what is going on.
 */
fun parseServerUrl(input: String): Result<HttpUrl> {
    val trimmed = input.trim()
    if (trimmed.startsWith(
            "http://",
            ignoreCase = true,
        )
    ) {
        return Result.failure(ServerUrlException(ServerUrlProblem.NotHttps))
    }
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    val url =
        withScheme
            .toHttpUrlOrNull()
            ?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }
            ?.takeIf { it.query == null && it.fragment == null }
            ?.let { if (it.encodedPath.endsWith("/")) it else it.newBuilder().addPathSegment("").build() }
    return url?.let { Result.success(it) } ?: Result.failure(ServerUrlException(ServerUrlProblem.Invalid))
}

class ServerUrlException(
    val problem: ServerUrlProblem,
) : IllegalArgumentException(problem.name)

/** Mirrors the server's name policy: 1-64 characters after trimming, no control or bidi override characters. */
fun isValidDeviceName(name: String): Boolean {
    val trimmed = name.trim()
    val length = trimmed.codePointCount(0, trimmed.length)
    return length in 1..MAX_DEVICE_NAME_LENGTH && trimmed.codePoints().noneMatch(::isForbidden)
}

/** The suggested name: the device model, never anything personal. */
fun defaultDeviceName(model: String?): String {
    val name = StringBuilder()
    model.orEmpty().trim().codePoints().filter { !isForbidden(it) }.limit(MAX_DEVICE_NAME_LENGTH.toLong()).forEach {
        name.appendCodePoint(it)
    }
    return name.toString().trim().ifEmpty { FALLBACK_DEVICE_NAME }
}

private const val FALLBACK_DEVICE_NAME = "Android device"

private fun isForbidden(codePoint: Int): Boolean =
    Character.getType(codePoint) == Character.CONTROL.toInt() ||
        codePoint in BIDI_EMBEDDING_FIRST..BIDI_EMBEDDING_LAST ||
        codePoint in BIDI_ISOLATE_FIRST..BIDI_ISOLATE_LAST

// Bidirectional overrides the server refuses: U+202A-U+202E and U+2066-U+2069.
private const val BIDI_EMBEDDING_FIRST = 0x202A
private const val BIDI_EMBEDDING_LAST = 0x202E
private const val BIDI_ISOLATE_FIRST = 0x2066
private const val BIDI_ISOLATE_LAST = 0x2069
