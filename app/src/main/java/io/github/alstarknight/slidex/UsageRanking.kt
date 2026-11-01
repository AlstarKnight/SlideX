package io.github.alstarknight.slidex

/** One app's usage over the queried period. */
data class AppUsage(val packageName: String, val lastUsed: Long, val foregroundTime: Long)

/**
 * Packages for a usage card, best first: by last use for [UsageKind.RECENT], by time on screen for
 * [UsageKind.FREQUENT]. Apps that were never on screen and [exclude]d ones are skipped.
 */
fun rankUsage(usage: List<AppUsage>, kind: UsageKind, exclude: Set<String>, count: Int): List<String> =
    usage
        .filter { it.foregroundTime > 0 && it.packageName !in exclude }
        .sortedByDescending { if (kind == UsageKind.RECENT) it.lastUsed else it.foregroundTime }
        .map { it.packageName }
        .distinct()
        .take(count)

/** What a usage event says about an app's window, reduced to what counting time on screen needs. */
enum class UsageEventKind {
    /** An activity of the app came to the front. */
    RESUMED,

    /** An activity of the app left the front. */
    PAUSED,

    /** The screen went off or the phone shut down: nothing is on screen any more. */
    SCREEN_OFF,
}

class UsageEvent(val packageName: String, val className: String, val kind: UsageEventKind, val time: Long)

/**
 * How long each app was on screen between [start] and [now], from that period's usage events in order. An app counts
 * while any of its activities is in front; one whose first event is leaving the front was there since [start], and
 * one still in front counts until [now].
 */
fun foregroundTimes(events: List<UsageEvent>, start: Long, now: Long): Map<String, Long> {
    val totals = HashMap<String, Long>()
    // Activities in front by app, and since when the app has been.
    val resumed = HashMap<String, MutableSet<String>>()
    val since = HashMap<String, Long>()
    val seen = HashSet<String>()
    fun add(packageName: String, time: Long) {
        totals[packageName] = (totals[packageName] ?: 0L) + time
    }
    for (event in events) {
        val app = event.packageName
        when (event.kind) {
            UsageEventKind.RESUMED -> {
                val activities = resumed.getOrPut(app) { mutableSetOf() }
                if (activities.isEmpty()) since[app] = event.time
                activities += event.className
                seen += app
            }
            UsageEventKind.PAUSED -> {
                val activities = resumed[app]
                if (activities != null && activities.remove(event.className)) {
                    if (activities.isEmpty()) add(app, event.time - since.getValue(app))
                } else if (app !in seen) {
                    add(app, event.time - start)
                }
                seen += app
            }
            UsageEventKind.SCREEN_OFF -> {
                resumed.forEach { (packageName, activities) ->
                    if (activities.isNotEmpty()) add(packageName, event.time - since.getValue(packageName))
                    activities.clear()
                }
            }
        }
    }
    resumed.forEach { (packageName, activities) ->
        if (activities.isNotEmpty()) add(packageName, now - since.getValue(packageName))
    }
    return totals
}
