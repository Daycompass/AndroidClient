package org.daycompass.android.sync

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.daycompass.android.buffer.BufferDatabase
import org.daycompass.android.buffer.BufferedEvent
import org.daycompass.android.pairing.DeviceSetup
import org.daycompass.android.usage.UsageCollection
import org.daycompass.android.usage.hasUsageAccess
import java.util.concurrent.TimeUnit

/**
 * Sync scheduling. There is no Wi-Fi detection: any connected network is worth a try, which
 * covers the home network, a VPN such as Tailscale and public servers alike. An unreachable
 * server just leaves the buffer for the next attempt.
 */
object Sync {
    private const val PERIODIC_WORK = "sync"
    private const val DELAYED_WORK = "sync-after-rate-limit"
    private const val INTERVAL_HOURS = 1L
    private const val BACKOFF_MINUTES = 10L
    private const val DEFAULT_RETRY_AFTER_SECONDS = 15 * 60L
    private const val LOG_TAG = "DaycompassSync"
    private const val PREFS = "sync_status"
    private const val LAST_SYNC = "last_sync_millis"
    private const val NOT_BEFORE = "not_before_millis"
    private const val MILLIS_PER_SECOND = 1_000L

    /** Caps the status screen's list; the buffer itself has no such limit. */
    private const val BUFFER_DISPLAY_LIMIT = 200

    @Volatile private var engine: EventSync? = null

    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun engine(context: Context): EventSync =
        engine ?: synchronized(this) {
            engine ?: context.applicationContext.let { app ->
                EventSync(
                    dao = BufferDatabase.get(app).bufferDao(),
                    setup = DeviceSetup.get(app),
                    // Reason codes only: they say what was wrong, never what the event was.
                    logRejections = { Log.w(LOG_TAG, "server rejected events: $it") },
                ).also { engine = it }
            }
        }

    /**
     * Collects what is new, then uploads, unless a `Retry-After` is still running: then no
     * request is made at all, whichever way the sync was started. Blocks; call it off the
     * main thread.
     */
    fun runOnce(context: Context): SyncResult {
        if (hasUsageAccess(context)) UsageCollection.collector(context).collect()
        val now = System.currentTimeMillis()
        val waitMillis = prefs(context).getLong(NOT_BEFORE, 0) - now
        if (waitMillis > 0) return SyncResult.RateLimited(waitMillis / MILLIS_PER_SECOND)
        val result = engine(context).upload()
        if (result == SyncResult.Done) prefs(context).edit { putLong(LAST_SYNC, now) }
        if (result is SyncResult.RateLimited) {
            val delaySeconds = result.retryAfterSeconds ?: DEFAULT_RETRY_AFTER_SECONDS
            prefs(context).edit { putLong(NOT_BEFORE, now + delaySeconds * MILLIS_PER_SECOND) }
            scheduleAfter(context, delaySeconds)
        }
        return result
    }

    /** Idempotent. Hourly while any network is connected; failed attempts back off. */
    fun schedule(context: Context) {
        val request =
            PeriodicWorkRequestBuilder<SyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(connected)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build()
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** After a `429`: one more attempt once `Retry-After` has passed, never earlier. */
    private fun scheduleAfter(
        context: Context,
        delaySeconds: Long,
    ) {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(connected)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(DELAYED_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun lastSyncMillis(context: Context): Long? = prefs(context).getLong(LAST_SYNC, 0).takeIf { it > 0 }

    fun pendingEvents(context: Context): Int = BufferDatabase.get(context).bufferDao().eventCount()

    /** The most recently collected entries still waiting to be sent, newest first. */
    fun bufferedEvents(context: Context): List<BufferedEvent> =
        BufferDatabase.get(context).bufferDao().recentEvents(BUFFER_DISPLAY_LIMIT)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result =
        when (Sync.runOnce(applicationContext)) {
            SyncResult.Unreachable, SyncResult.ServerError -> {
                Result.retry()
            }

            // A 401 must never be retried on a timer; a 429 has its own delayed attempt.
            SyncResult.Done, SyncResult.NotPaired, SyncResult.NeedsRepairing, is SyncResult.RateLimited -> {
                Result
                    .success()
            }
        }
}
