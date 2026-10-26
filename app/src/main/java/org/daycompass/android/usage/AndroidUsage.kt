package org.daycompass.android.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

// UsageEvents.Event type values. The named constants are deprecated or need a newer API
// level than minSdk, but the values are stable across versions.
private const val TYPE_RESUMED = 1 // MOVE_TO_FOREGROUND, ACTIVITY_RESUMED since API 29
private const val TYPE_PAUSED = 2 // MOVE_TO_BACKGROUND, ACTIVITY_PAUSED since API 29
private const val TYPE_SCREEN_NON_INTERACTIVE = 16 // API 28
private const val TYPE_KEYGUARD_SHOWN = 17 // API 28
private const val TYPE_DEVICE_SHUTDOWN = 26 // API 29

/** Reads `UsageEvents` through [UsageStatsManager]. Requires usage access. */
class AndroidUsageEventSource(
    context: Context,
) : UsageEventSource {
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)

    override fun events(
        fromMillis: Long,
        toMillis: Long,
    ): List<RawUsageEvent> {
        val events = usageStats.queryEvents(fromMillis, toMillis) ?: return emptyList()
        val event = UsageEvents.Event()
        val result = mutableListOf<RawUsageEvent>()
        while (events.getNextEvent(event)) {
            kindOf(event.eventType)?.let { result += RawUsageEvent(it, event.packageName, event.timeStamp) }
        }
        return result.sortedBy { it.timeMillis }
    }

    private fun kindOf(type: Int): UsageEventKind? =
        when (type) {
            TYPE_RESUMED -> UsageEventKind.Resumed
            TYPE_PAUSED -> UsageEventKind.Paused
            TYPE_SCREEN_NON_INTERACTIVE, TYPE_KEYGUARD_SHOWN -> UsageEventKind.ScreenOff
            TYPE_DEVICE_SHUTDOWN -> UsageEventKind.Shutdown
            else -> null
        }
}

/**
 * Whether the user granted usage access to this app in the system settings. Both AppOps
 * checks are deprecated at compileSdk 37; neither successor reaches back to minSdk 26.
 */
@Suppress("DEPRECATION")
fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java)
    val op = AppOpsManager.OPSTR_GET_USAGE_STATS
    val mode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(op, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(op, Process.myUid(), context.packageName)
        }
    return mode == AppOpsManager.MODE_ALLOWED
}

/** This app and the current home screen app: time spent there is not app usage. */
fun ignoredPackages(context: Context): Set<String> {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val launcher =
        context.packageManager
            .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
    return setOfNotNull(context.packageName, launcher)
}
