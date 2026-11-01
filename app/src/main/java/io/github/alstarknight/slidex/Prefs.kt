package io.github.alstarknight.slidex

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** How the panel is opened: a swipe from the edge strip, or a tap on the floating icon. */
enum class TriggerMode { EDGE, BALL }

enum class BallShape { CIRCLE, SQUARE }

class Prefs(context: Context) {
    private val appContext = context.applicationContext
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_SERVICE_ENABLED, value) }

    var side: Side
        get() = Side.valueOf(prefs.getString(KEY_SIDE, Side.RIGHT.name)!!)
        set(value) = prefs.edit { putString(KEY_SIDE, value.name) }

    /** Vertical center of the handle, in percent of the screen height. */
    var handlePositionPercent: Int
        get() = prefs.getInt(KEY_HANDLE_POSITION, 40)
        set(value) = prefs.edit { putInt(KEY_HANDLE_POSITION, value) }

    var handleHeightDp: Int
        get() = prefs.getInt(KEY_HANDLE_HEIGHT, 160)
        set(value) = prefs.edit { putInt(KEY_HANDLE_HEIGHT, value) }

    var handleWidthDp: Int
        get() = prefs.getInt(KEY_HANDLE_WIDTH, 18)
        set(value) = prefs.edit { putInt(KEY_HANDLE_WIDTH, value) }

    var showHandle: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HANDLE, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_HANDLE, value) }

    /** Whether a swipe from the edge handle pulls the panel out under the finger, rather than opening it at once. */
    var followFinger: Boolean
        get() = prefs.getBoolean(KEY_FOLLOW_FINGER, true)
        set(value) = prefs.edit { putBoolean(KEY_FOLLOW_FINGER, value) }

    var panelWidthPercent: Int
        get() = prefs.getInt(KEY_PANEL_WIDTH, 85)
        set(value) = prefs.edit { putInt(KEY_PANEL_WIDTH, value) }

    /**
     * Packages the trigger hides in, or with a whitelist the only ones it shows in. Telling which app is in front
     * needs the accessibility service. Kept under the old blacklist key so an existing list carries over.
     */
    var listedApps: Set<String>
        get() = prefs.getStringSet(KEY_LISTED_APPS, null).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet(KEY_LISTED_APPS, value) }

    var appListMode: AppListMode
        get() = enumPref(KEY_APP_LIST_MODE, AppListMode.BLACKLIST)
        set(value) = prefs.edit { putString(KEY_APP_LIST_MODE, value.name) }

    /** Move the floating icon above the keyboard while one is shown; needs the accessibility service. */
    var avoidKeyboard: Boolean
        get() = prefs.getBoolean(KEY_AVOID_KEYBOARD, true)
        set(value) = prefs.edit { putBoolean(KEY_AVOID_KEYBOARD, value) }

    var hideInLandscape: Boolean
        get() = prefs.getBoolean(KEY_HIDE_IN_LANDSCAPE, false)
        set(value) = prefs.edit { putBoolean(KEY_HIDE_IN_LANDSCAPE, value) }

    /** Blur what is behind the panel (Android 12+). */
    var panelBlur: Boolean
        get() = prefs.getBoolean(KEY_PANEL_BLUR, false)
        set(value) = prefs.edit { putBoolean(KEY_PANEL_BLUR, value) }

    /** How much the screen behind the open panel is darkened, in percent. */
    var scrimPercent: Int
        get() = prefs.getInt(KEY_SCRIM, 35)
        set(value) = prefs.edit { putInt(KEY_SCRIM, value) }

    /** Fade the panel out while the brightness slider is dragged, to show the new brightness behind it. */
    var brightnessSeeThrough: Boolean
        get() = prefs.getBoolean(KEY_BRIGHTNESS_SEE_THROUGH, true)
        set(value) = prefs.edit { putBoolean(KEY_BRIGHTNESS_SEE_THROUGH, value) }

    var triggerMode: TriggerMode
        get() = enumPref(KEY_TRIGGER_MODE, TriggerMode.EDGE)
        set(value) = prefs.edit { putString(KEY_TRIGGER_MODE, value.name) }

    var ballShape: BallShape
        get() = enumPref(KEY_BALL_SHAPE, BallShape.CIRCLE)
        set(value) = prefs.edit { putString(KEY_BALL_SHAPE, value.name) }

    var ballSizeDp: Int
        get() = prefs.getInt(KEY_BALL_SIZE, 48)
        set(value) = prefs.edit { putInt(KEY_BALL_SIZE, value) }

    var ballOpacityPercent: Int
        get() = prefs.getInt(KEY_BALL_OPACITY, 100)
        set(value) = prefs.edit { putInt(KEY_BALL_OPACITY, value) }

    /** Center of the floating icon, as fractions of the screen width and height. */
    val ballX: Float get() = prefs.getFloat(KEY_BALL_X, 1f)
    val ballY: Float get() = prefs.getFloat(KEY_BALL_Y, 0.6f)

    fun setBallPosition(x: Float, y: Float) = prefs.edit {
        putFloat(KEY_BALL_X, x)
        putFloat(KEY_BALL_Y, y)
    }

    /** What a tap on the floating icon does: null opens the panel, otherwise it runs that action. */
    var ballTapAction: Action?
        get() = actionPref(KEY_BALL_TAP)
        set(value) = prefs.edit { putString(KEY_BALL_TAP, value?.name) }

    /** Whether the floating icon vibrates on a tap or long press, as far as the system's touch feedback allows. */
    var ballHaptics: Boolean
        get() = prefs.getBoolean(KEY_BALL_HAPTICS, true)
        set(value) = prefs.edit { putBoolean(KEY_BALL_HAPTICS, value) }

    /** The shortcuts a long press on the floating icon shows around it, one per direction. */
    var ballMenu: Map<Direction, Item>
        get() = jsonPref<Map<Direction, Item>>(KEY_BALL_MENU) ?: mapOf(
            Direction.UP to actionItem(Action.LOCK_SCREEN),
            Direction.DOWN to actionItem(Action.SCREENSHOT),
            Direction.LEFT to actionItem(Action.NOTIFICATIONS),
            Direction.RIGHT to actionItem(Action.POWER_MENU),
        )
        set(value) = prefs.edit { putString(KEY_BALL_MENU, json.encodeToString(value)) }

    /** The configs kept, in the order they were made; [ConfigStore] uses the one called [activeConfig]. */
    var configProfiles: List<ConfigProfile>
        get() = jsonPref<List<ConfigProfile>>(KEY_CONFIG_PROFILES)
            ?: listOf(ConfigProfile(DEFAULT_CONFIG, appContext.getString(R.string.config_default_name)))
        set(value) = prefs.edit { putString(KEY_CONFIG_PROFILES, json.encodeToString(value)) }

    var activeConfig: String
        get() = prefs.getString(KEY_ACTIVE_CONFIG, null) ?: DEFAULT_CONFIG
        set(value) = prefs.edit { putString(KEY_ACTIVE_CONFIG, value) }

    /** Fine-tuning of the panel's look on top of the theme. */
    var appearance: Appearance
        get() = jsonPref<Appearance>(KEY_APPEARANCE) ?: Appearance()
        set(value) = prefs.edit { putString(KEY_APPEARANCE, json.encodeToString(value)) }

    /** Moves the edge handle in one write, so it does not jump between the two values. */
    fun setHandlePlacement(side: Side, positionPercent: Int) = prefs.edit {
        putString(KEY_SIDE, side.name)
        putInt(KEY_HANDLE_POSITION, positionPercent)
    }

    /** Whether the service shows its ongoing notification; without it the service is not a foreground service. */
    var showNotification: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NOTIFICATION, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_NOTIFICATION, value) }

    /** Package of the chosen icon pack, or null for the apps' own icons. */
    var iconPack: String?
        get() = prefs.getString(KEY_ICON_PACK, null)
        set(value) = prefs.edit { putString(KEY_ICON_PACK, value) }

    var themePreset: ThemePreset
        get() = enumPref(KEY_THEME_PRESET, ThemePreset.STARRY)
        set(value) = prefs.edit { putString(KEY_THEME_PRESET, value.name) }

    var darkMode: DarkMode
        get() = enumPref(KEY_DARK_MODE, DarkMode.SYSTEM)
        set(value) = prefs.edit { putString(KEY_DARK_MODE, value.name) }

    private fun actionItem(action: Action) = ActionItem(appContext.getString(action.labelRes), action)

    /** A value kept as JSON, or null when it is not set or cannot be read. */
    private inline fun <reified T> jsonPref(key: String): T? {
        val text = prefs.getString(key, null) ?: return null
        return try {
            json.decodeFromString<T>(text)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun actionPref(key: String): Action? {
        val name = prefs.getString(key, null)
        return Action.entries.firstOrNull { it.name == name }
    }

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T {
        val name = prefs.getString(key, null)
        return enumValues<T>().firstOrNull { it.name == name } ?: default
    }

    companion object {
        const val KEY_SERVICE_ENABLED = "service_enabled"
        const val KEY_SIDE = "side"
        const val KEY_HANDLE_POSITION = "handle_position"
        const val KEY_HANDLE_HEIGHT = "handle_height"
        const val KEY_HANDLE_WIDTH = "handle_width"
        const val KEY_SHOW_HANDLE = "show_handle"
        const val KEY_FOLLOW_FINGER = "follow_finger"
        const val KEY_PANEL_WIDTH = "panel_width"
        const val KEY_PANEL_BLUR = "panel_blur"
        const val KEY_SCRIM = "scrim"
        const val KEY_BRIGHTNESS_SEE_THROUGH = "brightness_see_through"
        const val KEY_LISTED_APPS = "hidden_apps"
        const val KEY_APP_LIST_MODE = "app_list_mode"
        const val KEY_AVOID_KEYBOARD = "avoid_keyboard"
        const val KEY_HIDE_IN_LANDSCAPE = "hide_in_landscape"
        const val KEY_TRIGGER_MODE = "trigger_mode"
        const val KEY_BALL_SHAPE = "ball_shape"
        const val KEY_BALL_SIZE = "ball_size"
        const val KEY_BALL_OPACITY = "ball_opacity"
        const val KEY_BALL_X = "ball_x"
        const val KEY_BALL_Y = "ball_y"
        const val KEY_BALL_TAP = "ball_tap"
        const val KEY_BALL_HAPTICS = "ball_haptics"
        const val KEY_BALL_MENU = "ball_menu"
        const val KEY_CONFIG_PROFILES = "config_profiles"
        const val KEY_ACTIVE_CONFIG = "active_config"
        const val KEY_APPEARANCE = "appearance"
        const val KEY_SHOW_NOTIFICATION = "show_notification"
        const val KEY_ICON_PACK = "icon_pack"
        const val KEY_THEME_PRESET = "theme_preset"
        const val KEY_DARK_MODE = "dark_mode"

        /** The first config, kept where the only one was before there could be several. */
        const val DEFAULT_CONFIG = "default"

        private val json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
        }
    }
}
