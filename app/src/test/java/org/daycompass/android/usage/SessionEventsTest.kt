package org.daycompass.android.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEventsTest {
    private val app = "org.example.alpha"
    private val start = 1_800_000_000_250L

    @Test
    fun sessionBecomesOneEventInSeconds() {
        val events = Session(app, start, start + 299_600).toEvents()

        assertEquals(1, events.size)
        val event = events.single()
        assertEquals(1_800_000_000L, event.startTs)
        assertEquals(300L, event.durationSecs)
        assertEquals(app, event.appId)
        assertNull(event.label)
        assertNull(event.domain)
        assertTrue(Regex("[0-9a-f]{32}").matches(event.eventId))
    }

    @Test
    fun sameSessionAlwaysGetsTheSameId() {
        assertEquals(
            Session(app, start, start + 5_000).toEvents().single().eventId,
            Session(app, start, start + 9_000).toEvents().single().eventId,
        )
        assertNotEquals(
            Session(app, start, start + 5_000).toEvents().single().eventId,
            Session("org.example.beta", start, start + 5_000).toEvents().single().eventId,
        )
    }

    @Test
    fun sessionsUnderASecondAreDropped() {
        assertEquals(emptyList<Any>(), Session(app, start, start + 400).toEvents())
        assertEquals(1, Session(app, start, start + 600).toEvents().size)
    }

    @Test
    fun longSessionsAreSplitIntoDays() {
        val events = Session(app, start, start + (MAX_EVENT_SECONDS * 2 + 60) * 1_000).toEvents()

        assertEquals(listOf(MAX_EVENT_SECONDS, MAX_EVENT_SECONDS, 60L), events.map { it.durationSecs })
        val offsets = events.map { it.startTs - events[0].startTs }
        assertEquals(listOf(0L, MAX_EVENT_SECONDS, MAX_EVENT_SECONDS * 2), offsets)
        assertEquals(3, events.map { it.eventId }.toSet().size)
    }
}
