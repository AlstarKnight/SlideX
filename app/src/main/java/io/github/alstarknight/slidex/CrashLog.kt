package io.github.alstarknight.slidex

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps a report of each crash on the phone, so it can be copied from the About page and sent to the developer.
 * Nothing is sent anywhere by itself.
 */
object CrashLog {
    private const val FILE_NAME = "crashes.txt"
    private const val MAX_CHARS = 32_000

    /** Writes a report whenever the app is about to crash, then lets the crash go on as before. */
    fun install(context: Context) {
        val file = file(context)
        val details = details(context)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val report = crashReport(time, "$details\nThread: ${thread.name}", error.stackTraceToString())
                file.writeText(appendCrash(read(file), report, MAX_CHARS))
            } catch (e: IOException) {
                // Not kept this time.
            } finally {
                previous?.uncaughtException(thread, error)
            }
        }
    }

    /** All kept reports, oldest first; empty when there are none. */
    fun read(context: Context): String = read(file(context))

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun read(file: File): String = if (file.exists()) file.readText() else ""

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** The app's version and build, and the phone's maker, model and Android version. */
    private fun details(context: Context): String {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        val build = BuildConfig.BUILD_NUMBER.takeIf { it.isNotEmpty() }?.let { " #$it (${BuildConfig.BUILD_COMMIT})" }
        return "SlideX ${version.orEmpty()}${build.orEmpty()}\n" +
            "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }
}
