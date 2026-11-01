package io.github.alstarknight.slidex

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.text.Collator
import java.time.LocalDate
import java.time.ZoneId

/** Recently and frequently used apps and today's time on screen, read from the system's usage history. */
object UsageApps {

    /** Whether the user allowed usage access ("使用情况访问权限") on the system settings page. */
    fun hasAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun settingsIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** Up to [count] apps used in the last week that can be opened from a launcher. Call off the main thread. */
    fun load(context: Context, kind: UsageKind, count: Int): List<AppItem> {
        val now = System.currentTimeMillis()
        val stats = context.getSystemService(UsageStatsManager::class.java)
            .queryAndAggregateUsageStats(now - HISTORY_MS, now)
        val usage = stats.values.map { AppUsage(it.packageName, it.lastTimeUsed, it.totalTimeInForeground) }
        val launchable = launchableApps(context)
        val exclude = usage.map { it.packageName }.filterNot { it in launchable }.toSet()
        // Names are read only for the apps shown, which is much quicker than for all.
        return rankUsage(usage, kind, exclude, count).map { appItem(context, launchable.getValue(it)) }
    }

    /**
     * Today's time on screen, counted from the usage events since midnight like the system's screen time: in all apps
     * that can be opened from a launcher, and in the [count] of them used longest. Call off the main thread.
     */
    @SuppressLint("InlinedApi") // The event types have the same values on older versions, or never come there.
    fun loadToday(context: Context, count: Int): ScreenTime {
        val now = System.currentTimeMillis()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(start, now)
        val event = UsageEvents.Event()
        val list = ArrayList<UsageEvent>()
        while (events.getNextEvent(event)) {
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventKind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventKind.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.DEVICE_SHUTDOWN,
                -> UsageEventKind.SCREEN_OFF
                else -> continue
            }
            list += UsageEvent(event.packageName, event.className.orEmpty(), kind, event.timeStamp)
        }
        val launchable = launchableApps(context)
        val times = foregroundTimes(list, start, now).filter { (app, time) -> app in launchable && time > 0 }
        val longest = times.entries.sortedByDescending { it.value }.take(count)
        val apps = longest.map { (app, time) -> appItem(context, launchable.getValue(app)) to time }
        return ScreenTime(times.values.sum(), apps)
    }

    /**
     * Launcher entries by package. The home screen is left out, since it is in front between every two apps and would
     * always come first, and so is SlideX.
     */
    private fun launchableApps(context: Context): Map<String, List<ResolveInfo>> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(launcher, 0).groupBy { it.activityInfo.packageName } -
            setOfNotNull(context.packageName, homePackage(context))
    }

    /** The app item for a package's launcher [entries]; with several, the one last by name. */
    private fun appItem(context: Context, entries: List<ResolveInfo>): AppItem {
        val pm = context.packageManager
        val collator = Collator.getInstance()
        val (label, activity) = entries
            .map { it.loadLabel(pm).toString() to it.activityInfo }
            .sortedWith { a, b -> collator.compare(a.first, b.first) }
            .last()
        return AppItem(label, activity.packageName, activity.name)
    }

    private fun homePackage(context: Context): String? =
        context.packageManager
            .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName

    private const val HISTORY_MS = 7 * 24 * 60 * 60 * 1000L
}

/** Time on screen today in milliseconds: [totalMillis] in all apps, and the apps used longest with theirs. */
class ScreenTime(val totalMillis: Long, val apps: List<Pair<AppItem, Long>>)
