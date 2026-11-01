package io.github.alstarknight.slidex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything the panel shows: pages of cards. Stored as JSON and used for import/export. */
@Serializable
data class PanelConfig(val version: Int = 1, val pages: List<Page> = listOf(Page()))

@Serializable
data class Page(
    val cards: List<Card> = emptyList(),
    val style: PageStyle = PageStyle(),
    /** Apps in which the panel opens on this page instead of the first one, like the original app's 情景模式. */
    val sceneApps: Set<String> = emptySet(),
)

/** How one page looks; every page can have its own. */
@Serializable
data class PageStyle(
    /** Look preset of this page, or null to follow the one chosen in the settings. */
    val preset: ThemePreset? = null,
    /** LIGHT or DARK, or null to follow the settings. */
    val darkMode: DarkMode? = null,
    /** Picture behind the page: a file name under `files/backgrounds`, or null for the theme color. */
    val background: String? = null,
    val backgroundFit: BackgroundFit = BackgroundFit.CROP,
    /** How see-through the page's background is, in percent; 0 is opaque. */
    val backgroundTransparency: Int = 0,
    /** How see-through the cards are, in percent. */
    val cardTransparency: Int = 0,
)

/** How a background picture fills the page. It stays put while the cards scroll, so a long page never stretches it. */
@Serializable
enum class BackgroundFit {
    /** Fills the page, cutting off what does not fit, evenly from both sides. */
    CROP,

    /** As wide as the page, from the top; the theme color fills the rest when the picture is shorter. */
    FILL_WIDTH,

    /** Stretched to the page's size. */
    STRETCH,
}

val TRANSPARENCY_RANGE = 0..100

/** Look presets, after the interface styles of Minipad Next. */
@Serializable
enum class ThemePreset {
    /** Blue-purple gradients; "dreamy" when light, a starry night when dark. */
    STARRY,

    /** Material You: wallpaper colors on Android 12+, the Material baseline before that. */
    MATERIAL,

    /** Quiet paper look with thin borders and one blue accent. */
    PAPER,

    /** Warm cream and orange. */
    CLASSIC,
}

@Serializable
enum class DarkMode { SYSTEM, LIGHT, DARK }

@Serializable
sealed interface Card {
    val id: String
    val title: String

    /** Whether the title is shown above the card's content; edit mode always shows it. */
    val showTitle: Boolean
}

/** A titled grid of icons (apps, links, shortcuts), like the original app's cards. */
@Serializable
@SerialName("grid")
data class GridCard(
    override val id: String,
    override val title: String,
    val items: List<Item> = emptyList(),
    /** Icons per row. */
    val columns: Int = DEFAULT_COLUMNS,
    override val showTitle: Boolean = true,
    /** Whether the names are shown under the icons. */
    val showLabels: Boolean = true,
    /** Space between rows of icons in dp. */
    val rowGapDp: Int = DEFAULT_ROW_GAP,
    /**
     * Space between icons side by side in dp, the icons growing or shrinking to fill the rest of the width; null
     * gives them a set size for the number of [columns].
     */
    val columnGapDp: Int? = null,
) : Card

const val DEFAULT_COLUMNS = 4
val COLUMN_RANGE = 2..6
const val DEFAULT_ROW_GAP = 16
val ROW_GAP_RANGE = 0..40
val COLUMN_GAP_RANGE = 0..64

@Serializable
@SerialName("note")
data class NoteCard(
    override val id: String,
    override val title: String,
    val text: String = "",
    val heightDp: Int = AUTO_HEIGHT,
    override val showTitle: Boolean = true,
) : Card

/** Text shown as it is, like the original app's text module: a heading, a motto, a reminder. */
@Serializable
@SerialName("text")
data class TextCard(
    override val id: String,
    override val title: String,
    val text: String = "",
    /** Font size in sp. */
    val textSize: Int = DEFAULT_TEXT_SIZE,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val centered: Boolean = false,
    /** Text color as `#RRGGBB`, or null for the theme's text color. */
    val color: String? = null,
    /** Whether the text sits on a card like the others, or straight on the page. */
    val cardBackground: Boolean = false,
    val heightDp: Int = AUTO_HEIGHT,
) : Card {
    /** Only the text is shown; the title just names the card in edit mode. */
    override val showTitle: Boolean get() = false
}

const val DEFAULT_TEXT_SIZE = 18
val TEXT_SIZE_RANGE = 10..48

/** A sentence from the Hitokoto service (hitokoto.cn), like the original app's 一言 card; a tap shows another. */
@Serializable
@SerialName("hitokoto")
data class HitokotoCard(
    override val id: String,
    override val title: String,
    override val showTitle: Boolean = true,
) : Card

/** Height of text and note cards: [AUTO_HEIGHT] fits the content, anything else is a fixed height in dp. */
const val AUTO_HEIGHT = 0
val CARD_HEIGHT_RANGE = AUTO_HEIGHT..480

/** Live system information or controls: battery, brightness, volume, memory and storage. */
@Serializable
@SerialName("system")
data class SystemCard(
    override val id: String,
    override val title: String,
    val kind: SystemKind,
    override val showTitle: Boolean = true,
) : Card

@Serializable
enum class SystemKind { BATTERY, BRIGHTNESS, VOLUME, STORAGE }

/** Apps taken from the usage history: the most recently used or the most used ones. */
@Serializable
@SerialName("usage")
data class UsageCard(
    override val id: String,
    override val title: String,
    val kind: UsageKind,
    val columns: Int = DEFAULT_COLUMNS,
    override val showTitle: Boolean = true,
    val showLabels: Boolean = true,
    val rowGapDp: Int = DEFAULT_ROW_GAP,
    val columnGapDp: Int? = null,
) : Card

@Serializable
enum class UsageKind { RECENT, FREQUENT }

/** How long each app was on screen today, the longest first. */
@Serializable
@SerialName("screentime")
data class ScreenTimeCard(
    override val id: String,
    override val title: String,
    override val showTitle: Boolean = true,
) : Card

/** Another app's home-screen widget, hosted in the panel under the system-assigned [widgetId]. */
@Serializable
@SerialName("widget")
data class WidgetCard(
    override val id: String,
    override val title: String,
    val widgetId: Int,
    val heightDp: Int = DEFAULT_WIDGET_HEIGHT,
    override val showTitle: Boolean = true,
) : Card

const val DEFAULT_WIDGET_HEIGHT = 180
val WIDGET_HEIGHT_RANGE = 60..480
const val WIDGET_HEIGHT_STEP = 20

@Serializable
sealed interface Item {
    val label: String
}

@Serializable
@SerialName("app")
data class AppItem(override val label: String, val packageName: String, val activityName: String) : Item

/** A web address, an app scheme such as `alipays://…`, or an `intent:` URI. */
@Serializable
@SerialName("link")
data class LinkItem(override val label: String, val uri: String) : Item

@Serializable
@SerialName("action")
data class ActionItem(override val label: String, val action: Action) : Item

@Serializable
enum class Action {
    NOTIFICATIONS,
    QUICK_SETTINGS,
    RECENTS,
    HOME,
    BACK,
    LOCK_SCREEN,
    SCREENSHOT,
    SPLIT_SCREEN,
    PLAY_PAUSE,
    NEXT_TRACK,
    PREVIOUS_TRACK,
    VOLUME_UP,
    VOLUME_DOWN,
    FLASHLIGHT,
    VOICE_ASSISTANT,
    WIFI,
    BLUETOOTH,
    MOBILE_DATA,
    HOTSPOT,
    LOCATION,
    NFC,
    AIRPLANE_MODE,
    AUTO_ROTATE,
    FORCE_LANDSCAPE,
    FORCE_PORTRAIT,
    AUTO_BRIGHTNESS,
    KEEP_SCREEN_ON,
    EYE_CARE,
    SILENT,
    VIBRATE,
    POWER_MENU,
    APP_DRAWER,
    CAMERA,
    GALLERY,
    DIALER,
    SETTINGS,
}

/** The four ways out of the floating icon; its long press shows a shortcut in each. */
@Serializable
enum class Direction { UP, DOWN, LEFT, RIGHT }

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    classDiscriminator = "type"
}

fun PanelConfig.toJson(): String = json.encodeToString(this)

/** Parses a config file; throws [IllegalArgumentException] (incl. SerializationException) when it is not valid. */
fun parseConfig(text: String): PanelConfig {
    val config = json.decodeFromString<PanelConfig>(text)
    return if (config.pages.isEmpty()) config.copy(pages = listOf(Page())) else config
}
