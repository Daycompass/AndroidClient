package org.daycompass.android.usage

import org.daycompass.android.api.Event
import java.security.MessageDigest
import java.util.Locale

/** The server accepts at most one day per event. */
const val MAX_EVENT_SECONDS = 86_400L

private const val MILLIS_PER_SECOND = 1_000L
private const val EVENT_ID_BYTES = 16

/**
 * Turns a session into wire events: Unix seconds, at most [MAX_EVENT_SECONDS] each, sessions
 * shorter than a second dropped. The `event_id` depends only on the app and the start time,
 * so reading the same session again never produces a second event. No label, no domain.
 */
fun Session.toEvents(): List<Event> {
    val startTs = Math.floorDiv(startMillis, MILLIS_PER_SECOND)
    val totalSeconds = (endMillis - startMillis + MILLIS_PER_SECOND / 2) / MILLIS_PER_SECOND
    if (totalSeconds < 1) return emptyList()
    return (0 until (totalSeconds + MAX_EVENT_SECONDS - 1) / MAX_EVENT_SECONDS).map { part ->
        val offset = part * MAX_EVENT_SECONDS
        Event(
            eventId = eventId(packageName, startMillis, part),
            startTs = startTs + offset,
            durationSecs = minOf(MAX_EVENT_SECONDS, totalSeconds - offset),
            appId = packageName,
        )
    }
}

private fun eventId(
    packageName: String,
    startMillis: Long,
    part: Long,
): String {
    val input = "$packageName\u0000$startMillis\u0000$part".encodeToByteArray()
    val digest = MessageDigest.getInstance("SHA-256").digest(input)
    return digest.take(EVENT_ID_BYTES).joinToString("") { "%02x".format(Locale.ROOT, it) }
}
