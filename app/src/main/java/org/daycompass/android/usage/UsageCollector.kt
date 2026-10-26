package org.daycompass.android.usage

import org.daycompass.android.buffer.BufferDao
import org.daycompass.android.buffer.CollectorState
import org.daycompass.android.buffer.toBuffered

/** Reads usage events of a time window, oldest first. */
fun interface UsageEventSource {
    fun events(
        fromMillis: Long,
        toMillis: Long,
    ): List<RawUsageEvent>
}

/**
 * Moves foreground sessions from Android's usage events into the local buffer. Works without
 * a network; the buffer is uploaded separately.
 */
class UsageCollector(
    private val dao: BufferDao,
    private val source: UsageEventSource,
    /** Packages never reported: this app itself and the launcher. */
    private val ignoredPackages: () -> Set<String>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Called once pairing succeeded: collection covers usage from now on, never before. */
    fun start() {
        dao.startCollecting(CollectorState(cursorMillis = clock()))
    }

    /** Collects everything since the last run. Does nothing before [start]. */
    @Synchronized
    fun collect() {
        val state = dao.collectorState() ?: return
        val now = clock()
        // A clock set back since the last run: wait until it has caught up again.
        if (now <= state.cursorMillis) return
        val ignored = ignoredPackages()
        val result = buildSessions(source.events(state.cursorMillis, now), now, state.openSession())
        val events =
            result.sessions
                .filter { it.packageName !in ignored }
                .flatMap { it.toEvents() }
                .map { it.toBuffered() }
        dao.saveRun(events, state.advancedTo(now, result.open))
    }
}

private fun CollectorState.openSession(): OpenSession? =
    if (openPackage != null && openStartMillis != null) {
        OpenSession(openPackage, openStartMillis, openPausedAtMillis)
    } else {
        null
    }

private fun CollectorState.advancedTo(
    cursor: Long,
    open: OpenSession?,
) = copy(
    cursorMillis = cursor,
    openPackage = open?.packageName,
    openStartMillis = open?.startMillis,
    openPausedAtMillis = open?.pausedAtMillis,
)
