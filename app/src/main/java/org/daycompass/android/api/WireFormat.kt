package org.daycompass.android.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire shapes from the backend's docs/wire-format.md. Classes holding a pairing code or a
// token are deliberately not data classes, so their default toString cannot leak them.

@Serializable
internal class PairRequest(
    @SerialName("pairing_code") val pairingCode: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String,
)

@Serializable
internal class PairResponse(
    @SerialName("device_token") val deviceToken: String,
    val device: DeviceView,
    @SerialName("server_time") val serverTime: Long,
)

@Serializable
internal data class DeviceView(
    val id: String,
    val name: String,
    val platform: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("last_seen_at") val lastSeenAt: Long,
    @SerialName("revoked_at") val revokedAt: Long? = null,
)

@Serializable
internal data class DeviceMeResponse(
    val id: String,
    val name: String,
    val platform: String,
    @SerialName("server_time") val serverTime: Long,
)

@Serializable
internal data class IngestRequest(
    val events: List<EventInput>,
)

@Serializable
internal data class EventInput(
    @SerialName("event_id") val eventId: String,
    @SerialName("start_ts") val startTs: Long,
    @SerialName("duration_secs") val durationSecs: Long,
    @SerialName("app_id") val appId: String,
    val label: String? = null,
    val domain: String? = null,
)

@Serializable
internal data class IngestResponse(
    val results: List<EventResultView>,
    @SerialName("server_time") val serverTime: Long,
)

@Serializable
internal data class EventResultView(
    val index: Int,
    val status: String,
    val reason: String? = null,
)

@Serializable
internal data class ErrorBody(
    val error: ErrorDetail,
)

@Serializable
internal data class ErrorDetail(
    val code: String,
)
