package io.github.alstarknight.slidex

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.location.LocationManagerCompat
import androidx.core.net.toUri
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.URISyntaxException

val Action.labelRes: Int
    @StringRes get() = when (this) {
        Action.NOTIFICATIONS -> R.string.action_notifications
        Action.QUICK_SETTINGS -> R.string.action_quick_settings
        Action.RECENTS -> R.string.action_recents
        Action.HOME -> R.string.action_home
        Action.BACK -> R.string.action_back
        Action.LOCK_SCREEN -> R.string.action_lock_screen
        Action.SCREENSHOT -> R.string.action_screenshot
        Action.SPLIT_SCREEN -> R.string.action_split_screen
        Action.PLAY_PAUSE -> R.string.action_play_pause
        Action.NEXT_TRACK -> R.string.action_next_track
        Action.PREVIOUS_TRACK -> R.string.action_previous_track
        Action.VOLUME_UP -> R.string.action_volume_up
        Action.VOLUME_DOWN -> R.string.action_volume_down
        Action.FLASHLIGHT -> R.string.action_flashlight
        Action.VOICE_ASSISTANT -> R.string.action_voice_assistant
        Action.WIFI -> R.string.action_wifi
        Action.BLUETOOTH -> R.string.action_bluetooth
        Action.MOBILE_DATA -> R.string.action_mobile_data
        Action.HOTSPOT -> R.string.action_hotspot
        Action.LOCATION -> R.string.action_location
        Action.NFC -> R.string.action_nfc
        Action.AIRPLANE_MODE -> R.string.action_airplane_mode
        Action.AUTO_ROTATE -> R.string.action_auto_rotate
        Action.FORCE_LANDSCAPE -> R.string.action_force_landscape
        Action.FORCE_PORTRAIT -> R.string.action_force_portrait
        Action.AUTO_BRIGHTNESS -> R.string.action_auto_brightness
        Action.KEEP_SCREEN_ON -> R.string.action_keep_screen_on
        Action.EYE_CARE -> R.string.action_eye_care
        Action.SILENT -> R.string.action_silent
        Action.VIBRATE -> R.string.action_vibrate
        Action.POWER_MENU -> R.string.action_power_menu
        Action.APP_DRAWER -> R.string.action_app_drawer
        Action.CAMERA -> R.string.action_camera
        Action.GALLERY -> R.string.action_gallery
        Action.DIALER -> R.string.action_dialer
        Action.SETTINGS -> R.string.action_settings
    }

val Action.iconRes: Int
    @DrawableRes get() = when (this) {
        Action.NOTIFICATIONS -> R.drawable.ic_action_notifications
        Action.QUICK_SETTINGS -> R.drawable.ic_action_tune
        Action.RECENTS -> R.drawable.ic_action_overview_key
        Action.HOME -> R.drawable.ic_action_home
        Action.BACK -> R.drawable.ic_action_arrow_back
        Action.LOCK_SCREEN -> R.drawable.ic_action_lock
        Action.SCREENSHOT -> R.drawable.ic_action_screenshot
        Action.SPLIT_SCREEN -> R.drawable.ic_action_splitscreen
        Action.PLAY_PAUSE -> R.drawable.ic_action_play_pause
        Action.NEXT_TRACK -> R.drawable.ic_action_skip_next
        Action.PREVIOUS_TRACK -> R.drawable.ic_action_skip_previous
        Action.VOLUME_UP -> R.drawable.ic_action_volume_up
        Action.VOLUME_DOWN -> R.drawable.ic_action_volume_down
        Action.FLASHLIGHT -> R.drawable.ic_action_flashlight_on
        Action.VOICE_ASSISTANT -> R.drawable.ic_action_mic
        Action.WIFI -> R.drawable.ic_action_wifi
        Action.BLUETOOTH -> R.drawable.ic_action_bluetooth
        Action.MOBILE_DATA -> R.drawable.ic_action_signal_cellular_alt
        Action.HOTSPOT -> R.drawable.ic_action_wifi_tethering
        Action.LOCATION -> R.drawable.ic_action_location_on
        Action.NFC -> R.drawable.ic_action_nfc
        Action.AIRPLANE_MODE -> R.drawable.ic_action_flight
        Action.AUTO_ROTATE -> R.drawable.ic_action_screen_rotation
        Action.FORCE_LANDSCAPE -> R.drawable.ic_action_screen_lock_landscape
        Action.FORCE_PORTRAIT -> R.drawable.ic_action_screen_lock_portrait
        Action.AUTO_BRIGHTNESS -> R.drawable.ic_action_brightness_auto
        Action.KEEP_SCREEN_ON -> R.drawable.ic_action_coffee
        Action.EYE_CARE -> R.drawable.ic_action_visibility
        Action.SILENT -> R.drawable.ic_action_notifications_off
        Action.VIBRATE -> R.drawable.ic_action_vibration
        Action.POWER_MENU -> R.drawable.ic_action_power_settings_new
        Action.APP_DRAWER -> R.drawable.ic_action_apps
        Action.CAMERA -> R.drawable.ic_action_photo_camera
        Action.GALLERY -> R.drawable.ic_action_photo_library
        Action.DIALER -> R.drawable.ic_action_call
        Action.SETTINGS -> R.drawable.ic_action_settings
    }

/**
 * Switches show whether they are on; the ones that cannot be flipped by an app open the system switch instead, unless
 * Shizuku can flip them.
 */
val SWITCH_ACTIONS = setOf(
    Action.WIFI, Action.BLUETOOTH, Action.MOBILE_DATA, Action.HOTSPOT, Action.LOCATION, Action.NFC,
    Action.AIRPLANE_MODE, Action.AUTO_ROTATE, Action.FORCE_LANDSCAPE, Action.FORCE_PORTRAIT, Action.AUTO_BRIGHTNESS,
    Action.FLASHLIGHT, Action.KEEP_SCREEN_ON, Action.EYE_CARE, Action.SILENT, Action.VIBRATE,
)

/** Actions that change something right away, so the panel stays open and shows the new state. */
private val IN_PLACE_ACTIONS = setOf(
    Action.FLASHLIGHT, Action.AUTO_ROTATE, Action.FORCE_LANDSCAPE, Action.FORCE_PORTRAIT, Action.AUTO_BRIGHTNESS,
    Action.KEEP_SCREEN_ON, Action.EYE_CARE, Action.SILENT, Action.VIBRATE,
)

/** What the forced landscape and portrait switches turn the screen to; landscape still follows which side is down. */
private val FORCED_ORIENTATIONS = mapOf(
    Action.FORCE_LANDSCAPE to ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
    Action.FORCE_PORTRAIT to ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
)

/** Opens apps and links and runs shortcut actions for panel items. */
class ItemLauncher(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val camera = context.getSystemService(CameraManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val overlays = ScreenOverlays(context)
    private val scope = MainScope()
    private var torchOn = false
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            torchOn = enabled
            refreshSwitches()
        }
    }
    private val activeSwitches = MutableStateFlow<Set<Action>>(emptySet())

    /** The switch actions that are currently on. */
    val switches: StateFlow<Set<Action>> = activeSwitches

    init {
        camera.registerTorchCallback(torchCallback, handler)
    }

    /** Whether a switch has forced landscape or portrait on the screen. */
    val orientationForced: Boolean get() = overlays.orientation != null

    fun release() {
        scope.cancel()
        camera.unregisterTorchCallback(torchCallback)
        handler.removeCallbacksAndMessages(null)
        overlays.release()
    }

    /** Opens or runs a panel item. Returns true when it was done in place and the panel can stay open. */
    fun launch(item: Item): Boolean {
        when (item) {
            is AppItem -> start(appIntent(item))
            is LinkItem -> linkIntent(item.uri)?.let { start(it) } ?: toast(R.string.launch_failed)
            is ActionItem -> {
                if (item.action in IN_PLACE_ACTIONS) return toggle(item.action)
                val command = ShizukuShell.switchCommand(item.action, on = !isOn(item.action))
                    ?.takeIf { ShizukuShell.isReady() }
                if (command != null) {
                    flipWithShizuku(command)
                    return true
                }
                // Let the panel finish closing first so it is not in the screenshot and does not take the back key.
                handler.postDelayed({ perform(item.action) }, ACTION_DELAY_MS)
            }
        }
        return false
    }

    /** Re-reads the state of every switch; system settings can change while the panel is closed. */
    fun refreshSwitches() {
        activeSwitches.value = SWITCH_ACTIONS.filterTo(mutableSetOf(), ::isOn)
    }

    private fun isOn(action: Action): Boolean {
        val resolver = context.contentResolver
        return try {
            when (action) {
                Action.FLASHLIGHT -> torchOn
                Action.KEEP_SCREEN_ON -> overlays.keepAwake
                Action.EYE_CARE -> overlays.eyeCare
                Action.SILENT -> audio.ringerMode == AudioManager.RINGER_MODE_SILENT
                Action.VIBRATE -> audio.ringerMode == AudioManager.RINGER_MODE_VIBRATE
                Action.AUTO_ROTATE -> Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
                Action.FORCE_LANDSCAPE, Action.FORCE_PORTRAIT -> overlays.orientation == FORCED_ORIENTATIONS[action]
                Action.AUTO_BRIGHTNESS -> Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                Action.WIFI -> context.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled == true
                Action.BLUETOOTH -> Settings.Global.getInt(resolver, Settings.Global.BLUETOOTH_ON, 0) == 1
                Action.MOBILE_DATA -> context.getSystemService(TelephonyManager::class.java)?.isDataEnabled == true
                Action.LOCATION -> context.getSystemService(LocationManager::class.java)
                    ?.let(LocationManagerCompat::isLocationEnabled) == true
                Action.NFC -> NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
                Action.AIRPLANE_MODE -> Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
                else -> false
            }
        } catch (e: SecurityException) {
            false
        }
    }

    /** Flips an in-place switch; returns false when it needs a permission first (the settings page is opened instead). */
    private fun toggle(action: Action): Boolean {
        val done = when (action) {
            Action.FLASHLIGHT -> {
                toggleTorch()
                true
            }
            Action.KEEP_SCREEN_ON -> {
                overlays.keepAwake = !overlays.keepAwake
                true
            }
            Action.EYE_CARE -> {
                overlays.eyeCare = !overlays.eyeCare
                true
            }
            Action.AUTO_ROTATE -> toggleSystemSetting(Settings.System.ACCELEROMETER_ROTATION, on = 1, off = 0)
            Action.FORCE_LANDSCAPE, Action.FORCE_PORTRAIT -> {
                val orientation = FORCED_ORIENTATIONS.getValue(action)
                overlays.orientation = if (overlays.orientation == orientation) null else orientation
                true
            }
            Action.AUTO_BRIGHTNESS -> toggleSystemSetting(
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                on = Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                off = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Action.SILENT -> toggleRinger(AudioManager.RINGER_MODE_SILENT)
            Action.VIBRATE -> toggleRinger(AudioManager.RINGER_MODE_VIBRATE)
            else -> false
        }
        refreshSwitches()
        return done
    }

    private fun toggleSystemSetting(name: String, on: Int, off: Int): Boolean {
        if (!Settings.System.canWrite(context)) {
            toast(R.string.needs_write_settings)
            start(writeSettingsIntent(context))
            return false
        }
        val resolver = context.contentResolver
        try {
            val current = Settings.System.getInt(resolver, name, off)
            Settings.System.putInt(resolver, name, if (current == on) off else on)
        } catch (e: SecurityException) {
            toast(R.string.action_failed)
        }
        return true
    }

    /** Runs a switch's command through Shizuku, then shows the switch's new state once it has settled. */
    private fun flipWithShizuku(command: String) {
        scope.launch {
            if (!ShizukuShell.run(context, command)) toast(R.string.shizuku_failed)
            refreshSwitches()
            // Wi-Fi, Bluetooth and the like take a moment to turn on or off.
            delay(SWITCH_SETTLE_MS)
            refreshSwitches()
        }
    }

    /** Switches between [mode] and normal ringing. Leaving or entering silent mode needs Do Not Disturb access. */
    private fun toggleRinger(mode: Int): Boolean {
        val target = if (audio.ringerMode == mode) AudioManager.RINGER_MODE_NORMAL else mode
        val touchesSilent = target == AudioManager.RINGER_MODE_SILENT ||
            audio.ringerMode == AudioManager.RINGER_MODE_SILENT
        val policyAccess = context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        if (touchesSilent && !policyAccess) {
            toast(R.string.needs_dnd_access)
            start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return false
        }
        try {
            audio.ringerMode = target
        } catch (e: SecurityException) {
            toast(R.string.action_failed)
        }
        return true
    }

    private fun appIntent(item: AppItem): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(item.packageName, item.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    /** Starts the first of [intents] that the system can open. */
    private fun start(vararg intents: Intent) {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                // Try the next one.
            } catch (e: SecurityException) {
                // Try the next one.
            }
        }
        toast(R.string.launch_failed)
    }

    private fun perform(action: Action) {
        when (action) {
            Action.NOTIFICATIONS -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            Action.QUICK_SETTINGS -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            Action.RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
            Action.HOME -> global(AccessibilityService.GLOBAL_ACTION_HOME)
            Action.BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK)
            Action.SPLIT_SCREEN -> global(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            Action.LOCK_SCREEN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            } else {
                toast(R.string.action_needs_android_9)
            }
            Action.SCREENSHOT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            } else {
                toast(R.string.action_needs_android_9)
            }
            Action.PLAY_PAUSE -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            Action.NEXT_TRACK -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            Action.PREVIOUS_TRACK -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            Action.VOLUME_UP -> volume(AudioManager.ADJUST_RAISE)
            Action.VOLUME_DOWN -> volume(AudioManager.ADJUST_LOWER)
            Action.VOICE_ASSISTANT -> start(Intent(Intent.ACTION_VOICE_COMMAND))
            // Android 10+ no longer lets apps flip these, so without Shizuku open the system switch for them.
            Action.WIFI -> start(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS),
            )
            Action.BLUETOOTH -> start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            Action.MOBILE_DATA -> start(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                } else {
                    Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
                },
            )
            Action.HOTSPOT -> start(
                Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
                Intent().setClassName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity"),
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
            )
            Action.LOCATION -> start(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            Action.NFC -> start(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_NFC) else Intent(Settings.ACTION_NFC_SETTINGS),
            )
            Action.AIRPLANE_MODE -> start(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            Action.POWER_MENU -> global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            Action.CAMERA -> start(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            Action.GALLERY -> start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))
            Action.DIALER -> start(Intent(Intent.ACTION_DIAL))
            Action.SETTINGS -> start(Intent(Settings.ACTION_SETTINGS))
            // In-place switches never get here, and the panel opens the app drawer itself.
            Action.FLASHLIGHT, Action.AUTO_ROTATE, Action.FORCE_LANDSCAPE, Action.FORCE_PORTRAIT,
            Action.AUTO_BRIGHTNESS, Action.KEEP_SCREEN_ON, Action.EYE_CARE, Action.SILENT, Action.VIBRATE,
            Action.APP_DRAWER,
            -> Unit
        }
    }

    private fun global(action: Int) {
        val service = ActionAccessibilityService.instance
        if (service == null) {
            toast(R.string.action_needs_accessibility)
            start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        service.performGlobalAction(action)
    }

    private fun mediaKey(keyCode: Int) {
        val time = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun volume(direction: Int) {
        audio.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
    }

    private fun toggleTorch() {
        try {
            val id = camera.cameraIdList.firstOrNull {
                camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (id == null) {
                toast(R.string.action_failed)
                return
            }
            camera.setTorchMode(id, !torchOn)
        } catch (e: CameraAccessException) {
            toast(R.string.action_failed)
        }
    }

    private fun toast(@StringRes message: Int) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ACTION_DELAY_MS = 350L
        private const val SWITCH_SETTLE_MS = 1_500L

        /** The system page where the user lets this app change system settings such as auto-rotate and brightness. */
        fun writeSettingsIntent(context: Context) =
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri())

        /** Builds the intent for a link item: `intent:` URIs are parsed, anything else is opened with ACTION_VIEW. */
        fun linkIntent(uri: String): Intent? {
            val text = uri.trim()
            if (text.isEmpty()) return null
            return try {
                if (text.startsWith("intent:") || text.contains("#Intent;")) {
                    Intent.parseUri(text, Intent.URI_INTENT_SCHEME)
                } else {
                    Intent(Intent.ACTION_VIEW, text.toUri())
                }
            } catch (e: URISyntaxException) {
                null
            }
        }
    }
}
