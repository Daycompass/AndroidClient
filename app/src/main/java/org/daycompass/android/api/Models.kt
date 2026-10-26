package org.daycompass.android.api

/** One observed event, as sent to `POST /api/v1/events`. Events carry no category. */
data class Event(
    /** 32 lowercase hex characters, stable for the observed event. */
    val eventId: String,
    /** Unix seconds, UTC. */
    val startTs: Long,
    /** 1-86400. */
    val durationSecs: Long,
    /** The package name. */
    val appId: String,
    val label: String? = null,
    /** A bare hostname, never a URL. */
    val domain: String? = null,
) {
    init {
        require(EVENT_ID.matches(eventId)) { "event_id must be 32 lowercase hex characters" }
    }

    private companion object {
        val EVENT_ID = Regex("[0-9a-f]{32}")
    }
}

data class Device(
    val id: String,
    val name: String,
    val platform: String,
    val createdAt: Long,
    val lastSeenAt: Long,
    val revokedAt: Long?,
)

/** Result of a successful pairing. Store [token] exactly once. */
data class Pairing(
    val token: DeviceToken,
    val device: Device,
    val serverTime: Long,
)

/** Result of `GET /api/v1/device/me`. Compare [serverTime] with the local clock. */
data class DeviceIdentity(
    val id: String,
    val name: String,
    val platform: String,
    val serverTime: Long,
)

sealed interface EventOutcome {
    data object Accepted : EventOutcome

    data object Duplicate : EventOutcome

    /** Permanently bad: drop it from the buffer and log [reason]. */
    data class Rejected(
        val reason: String?,
    ) : EventOutcome
}

/** [outcomes] lines up with the events that were sent, index for index. */
data class IngestResult(
    val outcomes: List<EventOutcome>,
    val serverTime: Long,
)
