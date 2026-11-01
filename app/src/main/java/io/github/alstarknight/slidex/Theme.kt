package io.github.alstarknight.slidex

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import kotlinx.serialization.Serializable

@get:StringRes
val ThemePreset.labelRes: Int
    get() = when (this) {
        ThemePreset.STARRY -> R.string.theme_starry
        ThemePreset.MATERIAL -> R.string.theme_material
        ThemePreset.PAPER -> R.string.theme_paper
        ThemePreset.CLASSIC -> R.string.theme_classic
    }

val ThemePreset.cardRadius: Dp
    get() = when (this) {
        ThemePreset.STARRY -> 22.dp
        ThemePreset.MATERIAL -> 24.dp
        ThemePreset.PAPER -> 12.dp
        ThemePreset.CLASSIC -> 10.dp
    }

/** What a preset adds on top of the Material color scheme and shapes. */
@Immutable
data class ThemeExtras(
    val dark: Boolean,
    val preset: ThemePreset,
    /** Gradient for the app title, or null for the plain text color. */
    val titleBrush: Brush?,
    /** Whether to sprinkle stars behind the panel header. */
    val stars: Boolean,
    val cardBorder: BorderStroke?,
)

val LocalThemeExtras = staticCompositionLocalOf {
    ThemeExtras(dark = false, preset = ThemePreset.STARRY, titleBrush = null, stars = false, cardBorder = null)
}

/**
 * Fine-tuning of the panel's look on top of the theme, set in the settings. Colors are `#RRGGBB`; null follows the
 * theme.
 */
@Serializable
@Immutable
data class Appearance(
    /** Corner radius of the cards in dp, or null for the theme's. */
    val cardRadiusDp: Int? = null,
    /** Space between cards in dp. */
    val cardGapDp: Int = 12,
    /** Space between the panel's edges and the cards in dp. */
    val panelPaddingDp: Int = 12,
    /** Space between a card's edges and its content in dp, across; up and down it is two thirds of that. */
    val cardPaddingDp: Int = 12,
    /** Size of the names under icons in sp. */
    val labelSizeSp: Int = 12,
    /** Color of pages without a picture. */
    val backgroundColor: String? = null,
    val switchOnColor: String? = null,
    val switchOffColor: String? = null,
)

val CARD_RADIUS_RANGE = 0..32
val CARD_GAP_RANGE = 0..32
val PANEL_PADDING_RANGE = 0..32
val CARD_PADDING_RANGE = 4..24
val LABEL_SIZE_RANGE = 9..16

val LocalAppearance = staticCompositionLocalOf { Appearance() }

/** A color from [Appearance]. */
fun appearanceColor(hex: String): Color = Color(hex.toColorInt())

/** Dark or white text, whichever reads better on [background]. */
fun contentColorOn(background: Color): Color = if (background.luminance() > 0.5f) Color(0xFF1D1B20) else Color.White

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var preset by remember { mutableStateOf(prefs.themePreset) }
    var mode by remember { mutableStateOf(prefs.darkMode) }
    var appearance by remember { mutableStateOf(prefs.appearance) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            preset = prefs.themePreset
            mode = prefs.darkMode
            appearance = prefs.appearance
        }
        prefs.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    CompositionLocalProvider(LocalAppearance provides appearance) {
        PresetTheme(preset, mode.isDark(), content)
    }
}

/** The Material theme and extras of one preset; pages with a look of their own use it too. */
@Composable
fun PresetTheme(preset: ThemePreset, dark: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = remember(preset, dark) { colorScheme(context, preset, dark) }
    val extras = ThemeExtras(
        dark = dark,
        preset = preset,
        titleBrush = if (preset == ThemePreset.STARRY) starryGradient(dark) else null,
        stars = preset == ThemePreset.STARRY && dark,
        cardBorder = if (preset == ThemePreset.PAPER) BorderStroke(1.dp, colors.outlineVariant) else null,
    )
    val cardRadius = LocalAppearance.current.cardRadiusDp?.dp ?: preset.cardRadius
    MaterialTheme(colorScheme = colors, shapes = Shapes(medium = RoundedCornerShape(cardRadius))) {
        CompositionLocalProvider(LocalThemeExtras provides extras, content = content)
    }
}

/**
 * A page's own look over [base], the look chosen in the settings. Text and icons outside a card take the page's
 * text color, since the page may be dark while the settings are light or the other way round, or one that reads on
 * the background color set in the settings.
 */
@Composable
fun PageTheme(style: PageStyle, base: ThemeExtras, content: @Composable () -> Unit) {
    val background = LocalAppearance.current.backgroundColor?.takeIf { style.background == null }
    PresetTheme(style.preset ?: base.preset, style.darkMode?.isDark() ?: base.dark) {
        val text = background?.let { contentColorOn(appearanceColor(it)) } ?: MaterialTheme.colorScheme.onSurface
        CompositionLocalProvider(LocalContentColor provides text, content = content)
    }
}

@Composable
private fun DarkMode.isDark(): Boolean = when (this) {
    DarkMode.SYSTEM -> isSystemInDarkTheme()
    DarkMode.LIGHT -> false
    DarkMode.DARK -> true
}

/** A small sample of the preset's accent, for the theme picker. */
@Composable
fun presetSwatch(preset: ThemePreset): Brush {
    val context = LocalContext.current
    return when (preset) {
        ThemePreset.STARRY -> starryGradient(dark = false)
        else -> SolidColor(colorScheme(context, preset, dark = false).primary)
    }
}

private fun starryGradient(dark: Boolean): Brush = Brush.linearGradient(
    if (dark) {
        listOf(Color(0xFF8FA2FF), Color(0xFFB3A6FF), Color(0xFFE29BF2))
    } else {
        listOf(Color(0xFF4F6BEF), Color(0xFF7B4FE0), Color(0xFFB34FD8))
    },
)

private fun colorScheme(context: Context, preset: ThemePreset, dark: Boolean): ColorScheme = when (preset) {
    ThemePreset.MATERIAL -> when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    ThemePreset.STARRY -> if (dark) {
        darkColorScheme(
            primary = Color(0xFFB3A6FF), onPrimary = Color(0xFF24166E),
            primaryContainer = Color(0xFF2E2A6C), onPrimaryContainer = Color(0xFFE6E1FF),
            secondary = Color(0xFFB3A6FF), onSecondary = Color(0xFF24166E),
            secondaryContainer = Color(0xFF2E2A6C), onSecondaryContainer = Color(0xFFE6E1FF),
            tertiary = Color(0xFFE29BF2),
            background = Color(0xFF0E1024), onBackground = Color(0xFFE8E7F8),
            surface = Color(0xFF0E1024), onSurface = Color(0xFFE8E7F8),
            surfaceVariant = Color(0xFF272B50), onSurfaceVariant = Color(0xFFB4B2D4),
            surfaceTint = Color(0xFFB3A6FF),
            outline = Color(0xFF8F8CB4), outlineVariant = Color(0xFF272B50),
            error = Color(0xFFFF8FA6), onError = Color(0xFF4A0418),
            surfaceContainerLowest = Color(0xFF0A0C1C), surfaceContainerLow = Color(0xFF13162F),
            surfaceContainer = Color(0xFF0E1024), surfaceContainerHigh = Color(0xFF1A1E40),
            surfaceContainerHighest = Color(0xFF20244A),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF5B47D6), onPrimary = Color.White,
            primaryContainer = Color(0xFFE9E4FF), onPrimaryContainer = Color(0xFF2A1B86),
            secondary = Color(0xFF5B47D6), onSecondary = Color.White,
            secondaryContainer = Color(0xFFE9E4FF), onSecondaryContainer = Color(0xFF2A1B86),
            tertiary = Color(0xFFB34FD8),
            background = Color(0xFFFAF9FF), onBackground = Color(0xFF1D1B2E),
            surface = Color(0xFFFAF9FF), onSurface = Color(0xFF1D1B2E),
            surfaceVariant = Color(0xFFE9E6F7), onSurfaceVariant = Color(0xFF514D6C),
            surfaceTint = Color(0xFF5B47D6),
            outline = Color(0xFF716D8E), outlineVariant = Color(0xFFE2DEF3),
            error = Color(0xFFC62A4B),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F4FE),
            surfaceContainer = Color(0xFFF1EFFC), surfaceContainerHigh = Color(0xFFF7F5FF),
            surfaceContainerHighest = Color.White,
        )
    }
    ThemePreset.PAPER -> if (dark) {
        darkColorScheme(
            primary = Color(0xFF8DA4FF), onPrimary = Color(0xFF0F1D57),
            primaryContainer = Color(0xFF232E58), onPrimaryContainer = Color(0xFFDCE3FF),
            secondary = Color(0xFF8DA4FF), onSecondary = Color(0xFF0F1D57),
            secondaryContainer = Color(0xFF232E58), onSecondaryContainer = Color(0xFFDCE3FF),
            background = Color(0xFF1E1E20), onBackground = Color(0xFFECECEC),
            surface = Color(0xFF1E1E20), onSurface = Color(0xFFECECEC),
            surfaceVariant = Color(0xFF28282B), onSurfaceVariant = Color(0xFFB4B5B9),
            surfaceTint = Color(0xFF8DA4FF),
            outline = Color(0xFF8C8D92), outlineVariant = Color(0xFF303033),
            error = Color(0xFFFF8A80), onError = Color(0xFF5C0A05),
            surfaceContainerLowest = Color(0xFF141415), surfaceContainerLow = Color(0xFF19191A),
            surfaceContainer = Color(0xFF19191A), surfaceContainerHigh = Color(0xFF222224),
            surfaceContainerHighest = Color(0xFF252527),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF3E63DD), onPrimary = Color.White,
            primaryContainer = Color(0xFFEBF0FF), onPrimaryContainer = Color(0xFF1E3A9E),
            secondary = Color(0xFF3E63DD), onSecondary = Color.White,
            secondaryContainer = Color(0xFFEBF0FF), onSecondaryContainer = Color(0xFF1E3A9E),
            background = Color.White, onBackground = Color(0xFF1F2023),
            surface = Color.White, onSurface = Color(0xFF1F2023),
            surfaceVariant = Color(0xFFF0F0ED), onSurfaceVariant = Color(0xFF55575D),
            surfaceTint = Color(0xFF3E63DD),
            outline = Color(0xFF76787E), outlineVariant = Color(0xFFE8E8E4),
            error = Color(0xFFD93036),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFAF8),
            surfaceContainer = Color(0xFFF7F7F5), surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color.White,
        )
    }
    ThemePreset.CLASSIC -> if (dark) {
        darkColorScheme(
            primary = Color(0xFFF0A04B), onPrimary = Color(0xFF3A2000),
            primaryContainer = Color(0xFF4A3317), onPrimaryContainer = Color(0xFFFFDCB8),
            secondary = Color(0xFFF0A04B), onSecondary = Color(0xFF3A2000),
            secondaryContainer = Color(0xFF3E3020), onSecondaryContainer = Color(0xFFFFDCB8),
            background = Color(0xFF1F2023), onBackground = Color(0xFFE3E1DB),
            surface = Color(0xFF1F2023), onSurface = Color(0xFFE3E1DB),
            surfaceVariant = Color(0xFF2C2D31), onSurfaceVariant = Color(0xFFA19E96),
            surfaceTint = Color(0xFFF0A04B),
            outline = Color(0xFF8D8A83), outlineVariant = Color(0xFF303236),
            error = Color(0xFFFF8A80), onError = Color(0xFF5C0A05),
            surfaceContainerLowest = Color(0xFF141517), surfaceContainerLow = Color(0xFF1B1C1E),
            surfaceContainer = Color(0xFF18191B), surfaceContainerHigh = Color(0xFF26272B),
            surfaceContainerHighest = Color(0xFF2A2B2F),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFFD9822B), onPrimary = Color.White,
            primaryContainer = Color(0xFFFBE6CF), onPrimaryContainer = Color(0xFF5A3108),
            secondary = Color(0xFFB0712E), onSecondary = Color.White,
            secondaryContainer = Color(0xFFF6E4CC), onSecondaryContainer = Color(0xFF4A2A06),
            background = Color(0xFFFFFDF6), onBackground = Color(0xFF2B2A26),
            surface = Color(0xFFFFFDF6), onSurface = Color(0xFF2B2A26),
            surfaceVariant = Color(0xFFEFE9D8), onSurfaceVariant = Color(0xFF6B6759),
            surfaceTint = Color(0xFFD9822B),
            outline = Color(0xFF8A8577), outlineVariant = Color(0xFFE2DAC4),
            error = Color(0xFFC0392B),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBF8EE),
            surfaceContainer = Color(0xFFF3EEDF), surfaceContainerHigh = Color(0xFFFAF6EA),
            surfaceContainerHighest = Color(0xFFFFFDF6),
        )
    }
}

/** A few faint stars, as in Minipad Next's starry header: x, y (fractions of the area), radius in dp, alpha. */
private val STARS = listOf(
    floatArrayOf(0.08f, 0.30f, 1.4f, 0.7f),
    floatArrayOf(0.22f, 0.72f, 1.0f, 0.45f),
    floatArrayOf(0.37f, 0.25f, 1.2f, 0.6f),
    floatArrayOf(0.51f, 0.60f, 1.0f, 0.5f),
    floatArrayOf(0.66f, 0.20f, 1.5f, 0.8f),
    floatArrayOf(0.79f, 0.66f, 1.0f, 0.4f),
    floatArrayOf(0.91f, 0.35f, 1.2f, 0.6f),
)

fun DrawScope.drawStars() {
    for ((x, y, radius, alpha) in STARS) {
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = radius.dp.toPx(),
            center = Offset(size.width * x, size.height * y),
        )
    }
}
