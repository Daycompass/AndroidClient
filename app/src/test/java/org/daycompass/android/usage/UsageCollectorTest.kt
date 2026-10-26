package org.daycompass.android.usage

import org.daycompass.android.buffer.BufferDao
import org.daycompass.android.buffer.BufferedEvent
import org.daycompass.android.buffer.CollectorState
import org.daycompass.android.usage.UsageEventKind.Paused
import org.daycompass.android.usage.UsageEventKind.Resumed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageCollectorTest {
    private val dao = FakeBufferDao()
    private var now = 1_000_000L
    private val recorded = mutableListOf<RawUsageEvent>()
    private val windows = mutableListOf<Pair<Long, Long>>()
    private val collector =
        UsageCollector(
            dao = dao,
            source = { from, to ->
                windows += from to to
                recorded.filter { it.timeMillis in from until to }
            },
            ignoredPackages = { setOf("org.daycompass.android", "org.example.launcher") },
            clock = { now },
        )

    @Test
    fun nothingIsCollectedBeforePairing() {
        now = 2_000_000
        collector.collect()

        assertEquals(emptyList<Pair<Long, Long>>(), windows)
        assertEquals(emptyMap<String, BufferedEvent>(), dao.events)
    }

    @Test
    fun collectionStartsAtPairingAndReadsOnlyNewWindows() {
        collector.start()
        recorded += RawUsageEvent(Resumed, "org.example.alpha", 1_100_000)
        recorded += RawUsageEvent(Paused, "org.example.alpha", 1_160_000)
        now = 1_200_000
        collector.collect()
        now = 1_300_000
        collector.collect()

        assertEquals(listOf(1_000_000L to 1_200_000L, 1_200_000L to 1_300_000L), windows)
        assertEquals(listOf(60L), dao.events.values.map { it.durationSecs })
        assertEquals(1_300_000L, dao.state?.cursorMillis)
    }

    @Test
    fun startingAgainKeepsTheOriginalCursor() {
        collector.start()
        now = 5_000_000
        collector.start()

        assertEquals(1_000_000L, dao.state?.cursorMillis)
    }

    @Test
    fun ownAppAndLauncherAreNotReported() {
        collector.start()
        recorded += RawUsageEvent(Resumed, "org.example.launcher", 1_010_000)
        recorded += RawUsageEvent(Resumed, "org.daycompass.android", 1_020_000)
        recorded += RawUsageEvent(Resumed, "org.example.alpha", 1_030_000)
        recorded += RawUsageEvent(Paused, "org.example.alpha", 1_040_000)
        now = 1_200_000
        collector.collect()

        assertEquals(
            setOf("org.example.alpha"),
            dao.events.values
                .map { it.appId }
                .toSet(),
        )
    }

    @Test
    fun sessionRunningAtTheEndIsFinishedInALaterRun() {
        collector.start()
        recorded += RawUsageEvent(Resumed, "org.example.alpha", 1_100_000)
        now = 1_200_000
        collector.collect()

        assertEquals(emptyMap<String, BufferedEvent>(), dao.events)
        assertEquals("org.example.alpha", dao.state?.openPackage)

        recorded += RawUsageEvent(Paused, "org.example.alpha", 1_250_000)
        now = 1_300_000
        collector.collect()

        assertEquals(listOf(150L), dao.events.values.map { it.durationSecs })
        assertNull(dao.state?.openPackage)
    }

    @Test
    fun clockSetBackDoesNotRewindTheCursor() {
        collector.start()
        now = 500_000
        collector.collect()

        assertEquals(emptyList<Pair<Long, Long>>(), windows)
        assertEquals(1_000_000L, dao.state?.cursorMillis)
    }
}

private class FakeBufferDao : BufferDao {
    var state: CollectorState? = null
    val events = linkedMapOf<String, BufferedEvent>()

    override fun collectorState() = state

    override fun startCollecting(state: CollectorState) {
        if (this.state == null) this.state = state
    }

    override fun saveCollectorState(state: CollectorState) {
        this.state = state
    }

    override fun insertEvents(events: List<BufferedEvent>) {
        events.forEach { this.events.putIfAbsent(it.eventId, it) }
    }
}
