package org.daycompass.android.sync

import okhttp3.HttpUrl
import org.daycompass.android.api.ApiResult
import org.daycompass.android.api.DeviceApiClient
import org.daycompass.android.api.DeviceToken
import org.daycompass.android.api.EventOutcome
import org.daycompass.android.buffer.BufferDao
import org.daycompass.android.buffer.BufferedEvent
import org.daycompass.android.pairing.DeviceSetup
import org.daycompass.android.pairing.SetupState

sealed interface SyncResult {
    /** Everything in the buffer was accounted for by the server. */
    data object Done : SyncResult

    data object NotPaired : SyncResult

    /** The server refused the token, now or earlier. No device request is made until pairing again. */
    data object NeedsRepairing : SyncResult

    /** `429`: keep the buffer, try again after [retryAfterSeconds]. */
    data class RateLimited(
        val retryAfterSeconds: Long?,
    ) : SyncResult

    /** No connection to the server, for example away from home without a VPN. */
    data object Unreachable : SyncResult

    /** The server answered with an error; try again later. */
    data object ServerError : SyncResult
}

sealed interface DeviceCheck {
    /** [clockSkewSeconds] is the server's clock minus this device's clock. */
    data class Ok(
        val clockSkewSeconds: Long,
    ) : DeviceCheck

    data object NeedsRepairing : DeviceCheck

    /** Not paired, unreachable, or any other reason the check could not run. */
    data object Unknown : DeviceCheck
}

/**
 * Uploads the buffer in batches. Events leave the buffer once the server has accounted for
 * them: accepted, duplicate, or rejected for good (only the reason code is logged). Anything
 * else keeps the buffer as it is; resending is always safe.
 */
class EventSync(
    private val dao: BufferDao,
    private val setup: DeviceSetup,
    private val clientFor: (HttpUrl) -> DeviceApiClient = { DeviceApiClient(it) },
    private val logRejections: (Map<String, Int>) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Synchronized
    fun upload(): SyncResult {
        val paired = setup.load()
        if (paired !is SetupState.Paired) return paired.toSyncResult()
        val client = clientFor(paired.serverUrl)
        var result: SyncResult? = null
        while (result == null) result = sendNextBatch(client, paired.token)
        return result
    }

    /** `GET /api/v1/device/me`, once per app start, never on a timer. */
    @Synchronized
    fun checkDevice(): DeviceCheck {
        val paired = setup.load() as? SetupState.Paired ?: return DeviceCheck.Unknown
        return when (val result = clientFor(paired.serverUrl).me(paired.token)) {
            is ApiResult.Success -> DeviceCheck.Ok(result.value.serverTime - clock() / MILLIS_PER_SECOND)
            ApiResult.Unauthenticated -> DeviceCheck.NeedsRepairing.also { setup.markUnauthenticated() }
            else -> DeviceCheck.Unknown
        }
    }

    /** Returns `null` while there is more to send. */
    private fun sendNextBatch(
        client: DeviceApiClient,
        token: DeviceToken,
    ): SyncResult? {
        // 500 events stay far below the 512 KiB body limit: each is a few hundred bytes at most.
        val batch = dao.oldestEvents(DeviceApiClient.MAX_BATCH_EVENTS)
        if (batch.isEmpty()) return SyncResult.Done
        return when (val result = client.sendEvents(token, batch.map(BufferedEvent::toEvent))) {
            is ApiResult.Success -> {
                val rejected =
                    result.value.outcomes
                        .filterIsInstance<EventOutcome.Rejected>()
                        .map { it.reason ?: "unknown" }
                if (rejected.isNotEmpty()) logRejections(rejected.groupingBy { it }.eachCount())
                dao.deleteEvents(batch.map { it.eventId })
                null
            }

            ApiResult.Unauthenticated -> {
                setup.markUnauthenticated()
                SyncResult.NeedsRepairing
            }

            is ApiResult.RateLimited -> {
                SyncResult.RateLimited(result.retryAfterSeconds)
            }

            is ApiResult.NetworkError -> {
                SyncResult.Unreachable
            }

            is ApiResult.ApiError, ApiResult.MalformedResponse -> {
                SyncResult.ServerError
            }
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}

private fun SetupState.toSyncResult() =
    when (this) {
        SetupState.NeedsRepairing -> SyncResult.NeedsRepairing
        else -> SyncResult.NotPaired
    }
