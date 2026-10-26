package org.daycompass.android.usage

import org.daycompass.android.usage.UsageEventKind.Paused
import org.daycompass.android.usage.UsageEventKind.Resumed
import org.daycompass.android.usage.UsageEventKind.ScreenOff
import org.daycompass.android.usage.UsageEventKind.Shutdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageSessionsTest {
    private val a = "org.example.alpha"
    private val b = "org.example.beta"

    private fun event(
        kind: UsageEventKind,
        packageName: String?,
        second: Long,
    ) = RawUsageEvent(kind, packageName, second * 1_000)

    private fun session(
        packageName: String,
        from: Long,
        to: Long,
    ) = Session(packageName, from * 1_000, to * 1_000)

    @Test
    fun resumeThenPauseIsOneSession() {
        val result = buildSessions(listOf(event(Resumed, a, 10), event(Paused, a, 70)), 100_000, open = null)

        assertEquals(listOf(session(a, 10, 70)), result.sessions)
        assertNull(result.open)
    }

    @Test
    fun anotherAppEndsTheRunningSession() {
        val result =
            buildSessions(
                listOf(event(Resumed, a, 10), event(Resumed, b, 40), event(Paused, b, 50)),
                windowEndMillis = 100_000,
                open = null,
            )

        assertEquals(listOf(session(a, 10, 40), session(b, 40, 50)), result.sessions)
    }

    @Test
    fun switchingActivitiesInsideOneAppStaysOneSession() {
        val events =
            listOf(
                event(Resumed, a, 10),
                RawUsageEvent(Paused, a, 30_000),
                RawUsageEvent(Resumed, a, 30_400),
                event(Paused, a, 60),
            )

        assertEquals(listOf(session(a, 10, 60)), buildSessions(events, 100_000, null).sessions)
    }

    @Test
    fun comingBackLaterIsANewSession() {
        val events = listOf(event(Resumed, a, 10), event(Paused, a, 20), event(Resumed, a, 50), event(Paused, a, 60))

        assertEquals(listOf(session(a, 10, 20), session(a, 50, 60)), buildSessions(events, 100_000, null).sessions)
    }

    @Test
    fun screenOffAndShutdownEndTheSession() {
        val events =
            listOf(event(Resumed, a, 10), event(ScreenOff, null, 30), event(Resumed, b, 40), event(Shutdown, null, 45))

        assertEquals(listOf(session(a, 10, 30), session(b, 40, 45)), buildSessions(events, 100_000, null).sessions)
    }

    @Test
    fun runningSessionIsCarriedIntoTheNextWindow() {
        val first = buildSessions(listOf(event(Resumed, a, 10)), windowEndMillis = 50_000, open = null)

        assertEquals(emptyList<Session>(), first.sessions)
        assertEquals(OpenSession(a, 10_000, null), first.open)

        val second = buildSessions(listOf(event(Paused, a, 80)), windowEndMillis = 100_000, open = first.open)
        assertEquals(listOf(session(a, 10, 80)), second.sessions)
    }

    @Test
    fun recentPauseAtTheWindowEndStaysOpenAnOldOneCloses() {
        val recent = buildSessions(listOf(event(Resumed, a, 10), RawUsageEvent(Paused, a, 49_500)), 50_000, null)
        assertEquals(OpenSession(a, 10_000, 49_500), recent.open)

        val old = buildSessions(listOf(event(Resumed, a, 10), event(Paused, a, 20)), 50_000, null)
        assertEquals(listOf(session(a, 10, 20)), old.sessions)
        assertNull(old.open)
    }

    @Test
    fun pauseOfAnotherAppIsIgnored() {
        val events = listOf(event(Resumed, a, 10), event(Paused, b, 20), event(Paused, a, 30))

        assertEquals(listOf(session(a, 10, 30)), buildSessions(events, 100_000, null).sessions)
    }
}
