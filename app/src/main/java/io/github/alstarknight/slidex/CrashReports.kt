package io.github.alstarknight.slidex

/*
 * The crash log kept by [CrashLog]: plain text, oldest report first, each starting with a line of
 * [CRASH_SEPARATOR] and the time.
 */

const val CRASH_SEPARATOR = "===== "

private val REPORT_START = Regex("^" + Regex.escape(CRASH_SEPARATOR), RegexOption.MULTILINE)

/** One report: when it happened, which build on which phone, and the stack trace. */
fun crashReport(time: String, details: String, stackTrace: String): String =
    "$CRASH_SEPARATOR$time\n$details\n$stackTrace\n"

/**
 * [log] with [report] added, dropping the oldest reports while the log is longer than [maxChars]; a report longer
 * than that on its own keeps only its start, where the error is.
 */
fun appendCrash(log: String, report: String, maxChars: Int): String {
    val reports = crashReports(log).toMutableList()
    reports += report.take(maxChars)
    while (reports.sumOf { it.length } > maxChars) reports.removeAt(0)
    return reports.joinToString("")
}

/** When each report in [log] was made, oldest first. */
fun crashTimes(log: String): List<String> =
    crashReports(log).map { it.lineSequence().first().removePrefix(CRASH_SEPARATOR) }

private fun crashReports(log: String): List<String> {
    val starts = REPORT_START.findAll(log).map { it.range.first }.toList()
    return starts.mapIndexed { i, start -> log.substring(start, starts.getOrElse(i + 1) { log.length }) }
}
