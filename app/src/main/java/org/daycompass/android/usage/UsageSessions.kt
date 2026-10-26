package org.daycompass.android.usage

/** The few kinds of `UsageEvents.Event` that decide when an app is in the foreground. */
enum class UsageEventKind { Resumed, Paused, ScreenOff, Shutdown }

data class RawUsageEvent(
    val kind: UsageEventKind,
    /** `null` for device-wide events such as the screen turning off. */
    val packageName: String?,
    val timeMillis: Long,
)

/** One stretch of time an app was in the foreground, in wall clock milliseconds. */
data class Session(
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long,
)

/**
 * A session still running when a collection run ended. [pausedAtMillis] is set when the app
 * was paused but nothing else has happened yet, so it may still come back.
 */
data class OpenSession(
    val packageName: String,
    val startMillis: Long,
    val pausedAtMillis: Long?,
)

data class SessionResult(
    val sessions: List<Session>,
    val open: OpenSession?,
)

/**
 * A pause followed by a resume of the same app within this gap is one session: Android pauses
 * and resumes an app when it switches between its own activities.
 */
const val SAME_APP_GAP_MILLIS = 2_000L

/**
 * Turns usage events of one collection window into sessions. [events] must be in time order;
 * [open] is the session left over from the previous window. A session ends when its app is
 * paused, another app comes to the foreground, the screen turns off or the device shuts down.
 */
fun buildSessions(
    events: List<RawUsageEvent>,
    windowEndMillis: Long,
    open: OpenSession?,
): SessionResult {
    val builder = SessionBuilder(open)
    events.forEach(builder::accept)
    return builder.finish(windowEndMillis)
}

private class SessionBuilder(
    private var current: OpenSession?,
) {
    private val sessions = mutableListOf<Session>()

    fun accept(event: RawUsageEvent) {
        when (event.kind) {
            UsageEventKind.Resumed -> resumed(event.packageName ?: return, event.timeMillis)
            UsageEventKind.Paused -> paused(event.packageName ?: return, event.timeMillis)
            UsageEventKind.ScreenOff, UsageEventKind.Shutdown -> close(event.timeMillis)
        }
    }

    fun finish(windowEndMillis: Long): SessionResult {
        val pausedAt = current?.pausedAtMillis
        // A pause old enough that the app cannot come back as the same session.
        if (pausedAt != null && windowEndMillis - pausedAt > SAME_APP_GAP_MILLIS) close(pausedAt)
        return SessionResult(sessions.toList(), current)
    }

    private fun resumed(
        packageName: String,
        time: Long,
    ) {
        val running = current
        if (running != null && running.packageName == packageName && running.continuesAt(time)) {
            current = running.copy(pausedAtMillis = null)
            return
        }
        close(time)
        current = OpenSession(packageName, time, pausedAtMillis = null)
    }

    private fun paused(
        packageName: String,
        time: Long,
    ) {
        val running = current ?: return
        if (running.packageName == packageName && running.pausedAtMillis == null) {
            current = running.copy(pausedAtMillis = time)
        }
    }

    private fun OpenSession.continuesAt(time: Long) =
        pausedAtMillis == null || time - pausedAtMillis <= SAME_APP_GAP_MILLIS

    /** Ends the running session at its pause, or at [time] if it was never paused. */
    private fun close(time: Long) {
        val running = current ?: return
        val end = running.pausedAtMillis ?: time
        if (end > running.startMillis) sessions += Session(running.packageName, running.startMillis, end)
        current = null
    }
}
