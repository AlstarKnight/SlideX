package io.github.alstarknight.slidex

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Rect
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Performs system actions (lock screen, screenshot, notifications…) for shortcut items, tracks which app is in
 * front so the trigger can follow the app list and the panel can open on that app's page, and where the keyboard is
 * so the floating icon can move out of its way. It looks only at window types and positions, never at what windows
 * show.
 */
class ActionAccessibilityService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    /** Whether a window class is an activity, by "package/class"; dialogs and keyboards are not. */
    private val activityChecks = HashMap<String, Boolean>()

    private val prefs by lazy { Prefs(this) }
    private var trackForeground = false
    private var trackKeyboard = false

    /** Whether a page opens in some apps, which lives in the panel config rather than the preferences. */
    private var hasScenes = false
    private var scenesWatch: Job? = null

    override fun onServiceConnected() {
        instance = this
        prefs.prefs.registerOnSharedPreferenceChangeListener(this)
        updateTracking()
        scenesWatch = MainScope().launch {
            ConfigStore.get(this@ActionAccessibilityService).config
                .map { config -> config.pages.any { it.sceneApps.isNotEmpty() } }
                .distinctUntilChanged()
                .collect {
                    hasScenes = it
                    updateTracking()
                }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        scenesWatch?.cancel()
        prefs.prefs.unregisterOnSharedPreferenceChangeListener(this)
        foreground.value = null
        keyboard.value = null
        return super.onUnbind(intent)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) = updateTracking()

    /**
     * Asks for window events only while a setting uses them: the app list and scenes need the app in front, the
     * floating icon's keyboard dodging needs the keyboard. Otherwise the system sends it no events at all.
     */
    private fun updateTracking() {
        trackForeground = prefs.serviceEnabled &&
            (prefs.appListMode == AppListMode.WHITELIST || prefs.listedApps.isNotEmpty() || hasScenes)
        trackKeyboard = prefs.serviceEnabled && prefs.triggerMode == TriggerMode.BALL && prefs.avoidKeyboard
        if (!trackForeground) foreground.value = null
        if (!trackKeyboard) keyboard.value = null
        var types = 0
        if (trackForeground || trackKeyboard) types = types or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (trackKeyboard) types = types or AccessibilityEvent.TYPE_WINDOWS_CHANGED
        val info = serviceInfo ?: return
        if (info.eventTypes != types) {
            info.eventTypes = types
            serviceInfo = info
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (trackForeground) updateForeground(event)
                if (trackKeyboard) updateKeyboard()
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> if (trackKeyboard) updateKeyboard()
        }
    }

    private fun updateForeground(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return
        // Keyboards, popups and the notification shade change window state too; only an activity means another app.
        if (isActivity(packageName, className)) foreground.value = packageName
    }

    private fun updateKeyboard() {
        val bounds = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?.let { window -> Rect().also(window::getBoundsInScreen) }
        keyboard.value = bounds?.takeUnless { it.isEmpty }?.top
    }

    private fun isActivity(packageName: String, className: String): Boolean =
        activityChecks.getOrPut("$packageName/$className") {
            try {
                packageManager.getActivityInfo(ComponentName(packageName, className), 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

    override fun onInterrupt() {}

    companion object {
        @Volatile
        var instance: ActionAccessibilityService? = null
            private set

        private val foreground = MutableStateFlow<String?>(null)

        /** Package of the app in front, or null while the service is off. */
        val foregroundPackage: StateFlow<String?> = foreground

        private val keyboard = MutableStateFlow<Int?>(null)

        /** Top edge of the on-screen keyboard in screen pixels, or null when none is shown or the service is off. */
        val keyboardTop: StateFlow<Int?> = keyboard

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val component = ComponentName(context, ActionAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
        }
    }
}
