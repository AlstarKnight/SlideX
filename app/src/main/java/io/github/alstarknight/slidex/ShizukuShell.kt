package io.github.alstarknight.slidex

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Runs shell commands through Shizuku (shizuku.rikka.app), which runs with adb's or root's rights once the user has
 * started it. That lets switches such as Wi-Fi and mobile data flip in place, which Android no longer allows apps.
 * The commands run in [ShellService], which Shizuku starts in a process of its own. Call from the main thread.
 */
object ShizukuShell {

    enum class State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    private const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"
    private const val CONNECT_TIMEOUT_MS = 5_000L
    private const val PERMISSION_REQUEST = 1

    private var service: IBinder? = null
    private var connecting: CompletableDeferred<IBinder>? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder
            binder?.let { connecting?.complete(it) }
            connecting = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    /** Whether Shizuku or Sui is running and has let SlideX use it. */
    fun isReady(): Boolean = state(null) == State.READY

    /** How far SlideX is from using Shizuku; without a [context], a missing Shizuku app reads as not running. */
    fun state(context: Context?): State = try {
        when {
            Shizuku.pingBinder() && !Shizuku.isPreV11() -> {
                val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                if (granted) State.READY else State.NO_PERMISSION
            }
            context != null && !isInstalled(context) -> State.NOT_INSTALLED
            else -> State.NOT_RUNNING
        }
    } catch (e: RuntimeException) {
        // The binder went away between the calls.
        State.NOT_RUNNING
    }

    private fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Shows Shizuku's question whether SlideX may use it; the settings read the answer when they come back. */
    fun requestPermission(context: Context) {
        try {
            // After "deny and don't ask again" Shizuku asks nothing, so open its app, where SlideX can be allowed.
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                openManager(context)
            } else {
                Shizuku.requestPermission(PERMISSION_REQUEST)
            }
        } catch (e: RuntimeException) {
            // Shizuku stopped in the meantime; the settings show that when they come back.
        }
    }

    /** Opens the Shizuku app, e.g. to start it. Returns false when it is not installed. */
    fun openManager(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE) ?: return false
        context.startActivity(intent)
        return true
    }

    /** Runs [command] with `sh -c`; returns whether it exited with 0. */
    suspend fun run(context: Context, command: String): Boolean {
        val binder = connect(context) ?: return false
        return withContext(Dispatchers.IO) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeString(command)
                binder.transact(ShellService.RUN, data, reply, 0)
                reply.readInt() == 0
            } catch (e: RemoteException) {
                false
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    private suspend fun connect(context: Context): IBinder? {
        service?.takeIf { it.pingBinder() }?.let { return it }
        val waiting = connecting ?: CompletableDeferred<IBinder>().also { deferred ->
            val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
                .daemon(false)
                .processNameSuffix("shell")
                .version(ShellService.VERSION)
            try {
                Shizuku.bindUserService(args, connection)
            } catch (e: RuntimeException) {
                return null
            }
            connecting = deferred
        }
        return withTimeoutOrNull(CONNECT_TIMEOUT_MS) { waiting.await() }.also { if (it == null) connecting = null }
    }

    /**
     * The shell command that turns [action]'s switch [on] or off, or null when there is none for it on this version
     * of Android: the hotspot needs settings a command cannot give, and airplane mode and location need Android 11.
     */
    fun switchCommand(action: Action, on: Boolean): String? {
        val enable = if (on) "enable" else "disable"
        return when (action) {
            Action.WIFI -> "svc wifi $enable"
            Action.BLUETOOTH -> "svc bluetooth $enable"
            Action.MOBILE_DATA -> "svc data $enable"
            Action.NFC -> "svc nfc $enable"
            Action.AIRPLANE_MODE ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "cmd connectivity airplane-mode $enable" else null
            Action.LOCATION ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "cmd location set-location-enabled $on" else null
            else -> null
        }
    }
}
