package io.github.alstarknight.slidex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt

/**
 * One card's own options, shown under it while it is edited in the panel: its title, the names under its icons and
 * their spacing, its height, and a text card's text style.
 */
@Composable
fun CardStyleControls(card: Card, store: ConfigStore) {
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        // A text card never shows its title.
        if (card !is TextCard) {
            SwitchRow(stringResource(R.string.card_show_title), card.showTitle) { show ->
                store.update { it.setShowTitle(card.id, show) }
            }
        }
        val showLabels = when (card) {
            is GridCard -> card.showLabels
            is UsageCard -> card.showLabels
            else -> null
        }
        if (showLabels != null) {
            SwitchRow(stringResource(R.string.card_show_labels), showLabels) { show ->
                store.update { it.setShowLabels(card.id, show) }
            }
        }
        val gaps = when (card) {
            is GridCard -> card.columnGapDp to card.rowGapDp
            is UsageCard -> card.columnGapDp to card.rowGapDp
            else -> null
        }
        if (gaps != null) {
            val (columnGap, rowGap) = gaps
            // The slider's first stop, left of 0, is "auto".
            val auto = stringResource(R.string.column_gap_auto)
            SliderRow(
                stringResource(R.string.card_column_gap),
                "dp",
                columnGap ?: AUTO_COLUMN_GAP_STOP,
                AUTO_COLUMN_GAP_STOP..COLUMN_GAP_RANGE.last,
                step = GAP_STEP,
                valueText = { if (it < 0) auto else "${it}dp" },
            ) { dp -> store.update { it.setColumnGap(card.id, dp.takeIf { gap -> gap >= 0 }) } }
            SliderRow(stringResource(R.string.card_row_gap), "dp", rowGap, ROW_GAP_RANGE, step = GAP_STEP) { dp ->
                store.update { it.setRowGap(card.id, dp) }
            }
            Hint(stringResource(R.string.card_gap_hint))
        }
        val height = when (card) {
            is TextCard -> card.heightDp
            is NoteCard -> card.heightDp
            is WidgetCard -> card.heightDp
            else -> null
        }
        if (height != null) {
            val widget = card is WidgetCard
            val auto = stringResource(R.string.height_auto)
            SliderRow(
                stringResource(R.string.card_height),
                "dp",
                height,
                if (widget) WIDGET_HEIGHT_RANGE else CARD_HEIGHT_RANGE,
                step = if (widget) WIDGET_HEIGHT_STEP else CARD_HEIGHT_STEP,
                valueText = { if (it == AUTO_HEIGHT) auto else "${it}dp" },
            ) { dp -> store.update { it.setCardHeight(card.id, dp) } }
        }
        if (card is TextCard) TextStyleRows(card, store)
    }
}

private const val CARD_HEIGHT_STEP = 8
private const val GAP_STEP = 4
private const val AUTO_COLUMN_GAP_STOP = -GAP_STEP

@Composable
private fun TextStyleRows(card: TextCard, store: ConfigStore) {
    fun update(transform: (TextCard) -> TextCard) = store.update { it.updateTextCard(card.id, transform) }
    SliderRow(stringResource(R.string.text_size), "sp", card.textSize, TEXT_SIZE_RANGE) { size ->
        update { it.copy(textSize = size) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = card.bold,
            onClick = { update { it.copy(bold = !it.bold) } },
            label = { Text(stringResource(R.string.text_bold)) },
        )
        FilterChip(
            selected = card.italic,
            onClick = { update { it.copy(italic = !it.italic) } },
            label = { Text(stringResource(R.string.text_italic)) },
        )
        FilterChip(
            selected = card.centered,
            onClick = { update { it.copy(centered = !it.centered) } },
            label = { Text(stringResource(R.string.text_centered)) },
        )
    }
    SwitchRow(stringResource(R.string.text_card_background), card.cardBackground) { on ->
        update { it.copy(cardBackground = on) }
    }
    Text(stringResource(R.string.text_color))
    FilterChip(
        selected = card.color == null,
        onClick = { update { it.copy(color = null) } },
        label = { Text(stringResource(R.string.text_color_theme)) },
    )
    TEXT_COLORS.chunked(5).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { color ->
                val chosen = color.equals(card.color, ignoreCase = true)
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(color.toColorInt()))
                        .border(
                            if (chosen) 3.dp else 1.dp,
                            if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        )
                        .clickable { update { it.copy(color = color) } },
                )
            }
        }
    }
}

private val TEXT_COLORS = listOf(
    "#FFFFFF", "#9E9E9E", "#000000", "#F44336", "#FF9800",
    "#FFEB3B", "#4CAF50", "#2196F3", "#9C27B0", "#E91E63",
)

/**
 * The look of the panel page at [pageIndex], edited on the page itself, after the original app's page settings: its
 * own theme, a background picture, and how see-through the page and its cards are. [onPickBackground] leaves the
 * panel for the system photo picker, which cannot show above it.
 */
@Composable
fun PageStyleEditor(pageIndex: Int, style: PageStyle, store: ConfigStore, onPickBackground: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    fun update(transform: (PageStyle) -> PageStyle) = store.update { it.setPageStyle(pageIndex, transform) }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.page_style), style = MaterialTheme.typography.titleMedium)
            SwitchRow(stringResource(R.string.page_own_theme), style.preset != null || style.darkMode != null) { on ->
                update { it.copy(preset = if (on) prefs.themePreset else null, darkMode = null) }
            }
            if (style.preset != null || style.darkMode != null) {
                ThemePicker(style.preset) { preset -> update { it.copy(preset = preset) } }
                ChoiceRow(
                    stringResource(R.string.setting_dark_mode),
                    listOf(
                        null to R.string.style_default,
                        DarkMode.LIGHT to R.string.dark_mode_light,
                        DarkMode.DARK to R.string.dark_mode_dark,
                    ),
                    style.darkMode,
                ) { mode -> update { it.copy(darkMode = mode) } }
            }
            Hint(stringResource(R.string.page_own_theme_hint))
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickBackground, modifier = Modifier.weight(1f)) {
                    val label = if (style.background == null) R.string.background_pick else R.string.background_change
                    Text(stringResource(label))
                }
                if (style.background != null) {
                    OutlinedButton(
                        onClick = {
                            update { it.copy(background = null) }
                            store.deleteUnused()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.background_remove))
                    }
                }
            }
            if (style.background != null) {
                ChoiceRow(
                    stringResource(R.string.background_fit),
                    listOf(
                        BackgroundFit.CROP to R.string.fit_crop,
                        BackgroundFit.FILL_WIDTH to R.string.fit_fill_width,
                        BackgroundFit.STRETCH to R.string.fit_stretch,
                    ),
                    style.backgroundFit,
                ) { fit -> update { it.copy(backgroundFit = fit) } }
                Hint(stringResource(R.string.background_fit_hint))
            }
            SliderRow(
                stringResource(R.string.background_transparency),
                "%",
                style.backgroundTransparency,
                TRANSPARENCY_RANGE,
            ) { value -> update { it.copy(backgroundTransparency = value) } }
            SliderRow(
                stringResource(R.string.card_transparency),
                "%",
                style.cardTransparency,
                TRANSPARENCY_RANGE,
            ) { value -> update { it.copy(cardTransparency = value) } }
            Hint(stringResource(R.string.transparency_hint))
        }
    }
}

/**
 * The apps in which the panel opens on this page, like the original app's scenes (情景模式); [onPick] shows the app
 * list. Knowing the app in front needs the accessibility service.
 */
@Composable
fun SceneEditor(apps: Set<String>, onPick: () -> Unit) {
    val context = LocalContext.current
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.scene_title), style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPick)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.scene_apps), Modifier.weight(1f))
                Text(
                    if (apps.isEmpty()) {
                        stringResource(R.string.listed_apps_none)
                    } else {
                        stringResource(R.string.listed_apps_count, apps.size)
                    },
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Hint(stringResource(R.string.scene_hint))
            if (!ActionAccessibilityService.isEnabled(context)) Hint(stringResource(R.string.needs_accessibility_hint))
        }
    }
}
