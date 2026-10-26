package org.daycompass.android.usage

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.daycompass.android.buffer.BufferDatabase
import java.util.concurrent.TimeUnit

/** Entry points for starting and scheduling usage collection. */
object UsageCollection {
    private const val WORK_NAME = "usage-collection"

    // Well inside the few days Android keeps raw usage events.
    private const val INTERVAL_HOURS = 1L

    @Volatile private var collector: UsageCollector? = null

    /** One collector per process, so runs from the worker and the app never overlap. */
    fun collector(context: Context): UsageCollector =
        collector ?: synchronized(this) {
            collector ?: context.applicationContext.let { app ->
                UsageCollector(
                    dao = BufferDatabase.get(app).bufferDao(),
                    source = AndroidUsageEventSource(app),
                    ignoredPackages = { ignoredPackages(app) },
                ).also { collector = it }
            }
        }

    /** After a successful pairing: collect from now on and schedule the periodic run. */
    fun start(context: Context) {
        collector(context).start()
        schedule(context)
    }

    /** Idempotent; keeps an already scheduled run. No network constraint: collection is local. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CollectWorker>(INTERVAL_HOURS, TimeUnit.HOURS).build()
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class CollectWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result {
        // Without usage access there is nothing to read; the permission screen asks for it.
        if (hasUsageAccess(applicationContext)) UsageCollection.collector(applicationContext).collect()
        return Result.success()
    }
}
