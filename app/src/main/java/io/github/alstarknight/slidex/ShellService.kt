package io.github.alstarknight.slidex

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import java.io.IOException
import kotlin.system.exitProcess

/**
 * Runs in a process Shizuku starts for SlideX, with Shizuku's rights, and runs the shell commands [ShizukuShell]
 * sends it. Shizuku creates it by its class name, so it must keep its name and no-argument constructor.
 */
class ShellService : Binder() {

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            RUN -> {
                val command = data.readString() ?: return false
                reply?.writeInt(run(command))
                return true
            }
            // Shizuku asks the service to end itself when it is replaced or removed.
            DESTROY -> exitProcess(0)
        }
        return super.onTransact(code, data, reply, flags)
    }

    private fun run(command: String): Int = try {
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        // Read the output, or a command that writes a lot would wait for room forever.
        process.inputStream.use { it.readBytes() }
        process.waitFor()
    } catch (e: IOException) {
        -1
    } catch (e: InterruptedException) {
        -1
    }

    companion object {
        const val RUN = IBinder.FIRST_CALL_TRANSACTION

        /** Shizuku's code for ending a user service. */
        private const val DESTROY = 16777115

        /** Raise when this class changes, so Shizuku starts the new code instead of reusing a running service. */
        const val VERSION = 1
    }
}
