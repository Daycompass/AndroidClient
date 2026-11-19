package org.daycompass.android.usage

import android.content.pm.PackageManager

/** Longest label the server accepts, counted in Unicode code points. */
const val MAX_LABEL_LENGTH = 128

/** Mirrors the server's label policy: no control/bidi-override characters, at most 128 code points. */
fun sanitizeLabel(raw: String): String? {
    val name = StringBuilder()
    raw.codePoints().filter { !isForbiddenInLabel(it) }.limit(MAX_LABEL_LENGTH.toLong()).forEach {
        name.appendCodePoint(it)
    }
    return name.toString().trim().ifEmpty { null }
}

private fun isForbiddenInLabel(codePoint: Int): Boolean =
    Character.isISOControl(codePoint) ||
        codePoint in BIDI_EMBEDDING_FIRST..BIDI_EMBEDDING_LAST ||
        codePoint in BIDI_ISOLATE_FIRST..BIDI_ISOLATE_LAST

// Bidirectional overrides the server refuses: U+202A-U+202E and U+2066-U+2069.
private const val BIDI_EMBEDDING_FIRST = 0x202A
private const val BIDI_EMBEDDING_LAST = 0x202E
private const val BIDI_ISOLATE_FIRST = 0x2066
private const val BIDI_ISOLATE_LAST = 0x2069

/** Resolves an app's sanitized display name, caching per package for the process lifetime. */
class AppLabelResolver(
    private val rawLabel: (String) -> String?,
) {
    private val cache = mutableMapOf<String, String?>()

    /** `null` when the package can't be resolved or its name sanitizes to nothing. */
    fun labelFor(appId: String): String? {
        if (!cache.containsKey(appId)) cache[appId] = rawLabel(appId)?.let(::sanitizeLabel)
        return cache[appId]
    }
}

/** Looks up a package's display name through [PackageManager]; never throws. */
fun packageManagerLabelResolver(packageManager: PackageManager) =
    AppLabelResolver { appId ->
        runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(appId, 0)).toString()
        }.getOrNull()
    }
