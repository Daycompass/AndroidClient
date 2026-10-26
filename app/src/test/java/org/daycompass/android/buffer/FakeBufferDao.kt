package org.daycompass.android.buffer

/** In-memory stand-in for the Room DAO. */
class FakeBufferDao : BufferDao {
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

    override fun oldestEvents(limit: Int) =
        events.values.sortedWith(compareBy({ it.startTs }, { it.eventId })).take(limit)

    override fun deleteEvents(eventIds: List<String>) {
        eventIds.forEach(events::remove)
    }

    override fun eventCount() = events.size

    override fun recentEvents(limit: Int) =
        events.values
            .sortedWith(
                compareByDescending<BufferedEvent> { it.startTs }.thenByDescending { it.eventId },
            ).take(limit)
}
