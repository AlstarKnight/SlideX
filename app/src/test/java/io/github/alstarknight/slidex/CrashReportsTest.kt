package io.github.alstarknight.slidex

import org.junit.Assert.assertEquals
import org.junit.Test

class CrashReportsTest {

    private fun report(time: String) =
        crashReport(time, "SlideX 0.1.0", "java.lang.IllegalStateException: $time\n\tat a.b.C.run(C.kt:1)")

    @Test
    fun reportsAddUpOldestFirst() {
        val log = appendCrash(appendCrash("", report("1"), maxChars = 10_000), report("2"), maxChars = 10_000)
        assertEquals(report("1") + report("2"), log)
        assertEquals(listOf("1", "2"), crashTimes(log))
    }

    @Test
    fun theOldestReportsGoWhenTheLogIsFull() {
        val max = report("1").length * 2
        var log = ""
        for (time in listOf("1", "2", "3")) log = appendCrash(log, report(time), max)
        assertEquals(listOf("2", "3"), crashTimes(log))
    }

    @Test
    fun aReportTooLongOnItsOwnKeepsItsStart() {
        val long = crashReport("1", "SlideX 0.1.0", "x".repeat(500))
        val log = appendCrash(report("0"), long, maxChars = 100)
        assertEquals(long.take(100), log)
        assertEquals(listOf("1"), crashTimes(log))
    }

    @Test
    fun anEmptyLogHasNoReports() {
        assertEquals(emptyList<String>(), crashTimes(""))
    }
}
