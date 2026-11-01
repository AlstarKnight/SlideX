package io.github.alstarknight.slidex

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageRankingTest {

    private val usage = listOf(
        AppUsage("chat", lastUsed = 300, foregroundTime = 10),
        AppUsage("video", lastUsed = 100, foregroundTime = 90),
        AppUsage("maps", lastUsed = 200, foregroundTime = 40),
        AppUsage("updater", lastUsed = 400, foregroundTime = 0),
    )

    @Test
    fun recentSortsByLastUse() {
        assertEquals(listOf("chat", "maps", "video"), rankUsage(usage, UsageKind.RECENT, emptySet(), 10))
    }

    @Test
    fun frequentSortsByTimeOnScreen() {
        assertEquals(listOf("video", "maps", "chat"), rankUsage(usage, UsageKind.FREQUENT, emptySet(), 10))
    }

    @Test
    fun excludedAppsAndTheCountAreRespected() {
        assertEquals(listOf("chat", "video"), rankUsage(usage, UsageKind.RECENT, setOf("maps"), 2))
        assertEquals(listOf("video"), rankUsage(usage, UsageKind.FREQUENT, emptySet(), 1))
    }

    private fun resumed(app: String, time: Long, activity: String = "main") =
        UsageEvent(app, activity, UsageEventKind.RESUMED, time)

    private fun paused(app: String, time: Long, activity: String = "main") =
        UsageEvent(app, activity, UsageEventKind.PAUSED, time)

    @Test
    fun timeOnScreenAddsUpEachVisit() {
        val events = listOf(resumed("chat", 10), paused("chat", 30), resumed("video", 30), paused("video", 80),
            resumed("chat", 80), paused("chat", 85))
        assertEquals(mapOf("chat" to 25L, "video" to 50L), foregroundTimes(events, start = 0, now = 100))
    }

    @Test
    fun anAppInFrontAtTheStartOrTheEndCountsFromTheStartOrUntilNow() {
        val events = listOf(paused("maps", 20), resumed("chat", 20))
        assertEquals(mapOf("maps" to 20L, "chat" to 80L), foregroundTimes(events, start = 0, now = 100))
    }

    @Test
    fun switchingActivitiesWithinAnAppIsOneVisit() {
        val events = listOf(resumed("chat", 10, "list"), resumed("chat", 20, "talk"), paused("chat", 21, "list"),
            paused("chat", 40, "talk"))
        assertEquals(mapOf("chat" to 30L), foregroundTimes(events, start = 0, now = 100))
    }

    @Test
    fun theScreenGoingOffEndsTheVisit() {
        val events = listOf(resumed("video", 10), UsageEvent("android", "", UsageEventKind.SCREEN_OFF, 50),
            paused("video", 51), resumed("video", 90))
        assertEquals(mapOf("video" to 50L), foregroundTimes(events, start = 0, now = 100))
    }

    @Test
    fun aRepeatedLeaveDoesNotCountTwice() {
        val events = listOf(resumed("chat", 10), paused("chat", 20), paused("chat", 30))
        assertEquals(mapOf("chat" to 10L), foregroundTimes(events, start = 0, now = 100))
    }
}
