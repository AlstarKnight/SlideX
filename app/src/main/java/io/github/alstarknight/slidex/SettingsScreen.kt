package io.github.alstarknight.slidex

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt

/**
 * The settings, laid out like the original app's: the service switch first, then one entry per topic, each
 * opening its own page.
 */
private enum class SettingsPage(@StringRes val title: Int, @StringRes val summary: Int) {
    PERMISSIONS(R.string.page_permissions, R.string.permissions_all_granted),
    TRIGGER(R.string.page_trigger, R.string.page_trigger_summary),
    THEME(R.string.page_theme, R.string.page_theme_summary),
    ADVANCED(R.string.page_advanced, R.string.page_advanced_summary),
    CONFIG(R.string.page_config, R.string.page_config_summary),
    ABOUT(R.string.page_about, R.string.page_about_summary),
}

/** Which special permissions SlideX holds; read again whenever the settings come back into view. */
private data class Permissions(
    val overlay: Boolean,
    val notifications: Boolean,
    val accessibility: Boolean,
    val writeSettings: Boolean,
    val dnd: Boolean,
    val usage: Boolean,
    val battery: Boolean,
    /** Optional, so it is not counted as missing. */
    val shizuku: ShizukuShell.State,
) {
    val missing: Int
        get() = listOf(overlay, notifications, accessibility, writeSettings, dnd, usage, battery).count { !it }
}

private fun readPermissions(context: Context) = Permissions(
    overlay = Settings.canDrawOverlays(context),
    notifications = hasNotificationPermission(context),
    accessibility = ActionAccessibilityService.isEnabled(context),
    writeSettings = Settings.System.canWrite(context),
    dnd = context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted,
    usage = UsageApps.hasAccess(context),
    battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
    shizuku = ShizukuShell.state(context),
)

@Composable
fun SettingsScreen(prefs: Prefs) {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(readPermissions(context)) }
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }

    LifecycleResumeEffect(Unit) {
        permissions = readPermissions(context)
        // Covers returning from the overlay permission screen and the service having been killed.
        if (prefs.serviceEnabled && permissions.overlay) PanelService.start(context)
        onPauseOrDispose {}
    }
    BackHandler(enabled = page != null) { page = null }

    Surface(Modifier.fillMaxSize()) {
        val current = page
        if (current == null) {
            SettingsHome(prefs, permissions, onOpen = { page = it })
            return@Surface
        }
        // A fresh page each time, so it starts scrolled to the top.
        key(current) {
            SettingsSubpage(current, onBack = { page = null }) {
                when (current) {
                    SettingsPage.PERMISSIONS -> PermissionSettings(permissions) {
                        permissions = readPermissions(context)
                    }
                    SettingsPage.TRIGGER -> TriggerSettings(prefs, permissions.accessibility)
                    SettingsPage.THEME -> ThemeSettings(prefs)
                    SettingsPage.ADVANCED -> AdvancedSettings(prefs, permissions.accessibility)
                    SettingsPage.CONFIG -> ConfigSettings()
                    SettingsPage.ABOUT -> AboutSettings()
                }
            }
        }
    }
}

@Composable
private fun SettingsHome(prefs: Prefs, permissions: Permissions, onOpen: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val titleBrush = LocalThemeExtras.current.titleBrush
        Text(
            stringResource(R.string.app_name),
            style = titleBrush?.let { MaterialTheme.typography.headlineMedium.copy(brush = it) }
                ?: MaterialTheme.typography.headlineMedium,
        )

        Section {
            var serviceEnabled by remember { mutableStateOf(prefs.serviceEnabled) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_enable), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (serviceEnabled) R.string.service_on else R.string.service_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (serviceEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Switch(
                    checked = serviceEnabled,
                    onCheckedChange = { on ->
                        serviceEnabled = on
                        prefs.serviceEnabled = on
                        when {
                            !on -> PanelService.stop(context)
                            permissions.overlay -> PanelService.start(context)
                            else -> context.startActivity(overlaySettingsIntent(context))
                        }
                    },
                )
            }
            if (!permissions.overlay) {
                PermissionHint(R.string.needs_overlay) { context.startActivity(overlaySettingsIntent(context)) }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            SettingsPage.entries.forEachIndexed { index, page ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
                val summary = if (page == SettingsPage.PERMISSIONS && permissions.missing > 0) {
                    stringResource(R.string.permissions_missing, permissions.missing)
                } else {
                    stringResource(page.summary)
                }
                PageEntry(pageIcon(page), stringResource(page.title), summary) { onOpen(page) }
            }
        }
    }
}

@Composable
private fun pageIcon(page: SettingsPage): Painter = when (page) {
    SettingsPage.PERMISSIONS -> painterResource(R.drawable.ic_action_lock)
    SettingsPage.TRIGGER -> painterResource(R.drawable.ic_action_tune)
    SettingsPage.THEME -> painterResource(R.drawable.ic_action_visibility)
    SettingsPage.ADVANCED -> painterResource(R.drawable.ic_action_settings)
    SettingsPage.CONFIG -> rememberVectorPainter(Icons.Default.Share)
    SettingsPage.ABOUT -> rememberVectorPainter(Icons.Default.Info)
}

/** An entry on the home page: icon, title, a line on what is inside, and an arrow. */
@Composable
private fun PageEntry(icon: Painter, title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSubpage(page: SettingsPage, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.safeDrawingPadding()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(page.title), style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun PermissionSettings(permissions: Permissions, onChanged: () -> Unit) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onChanged()
    }
    Section {
        PermissionRow(
            title = stringResource(R.string.permission_overlay),
            description = stringResource(R.string.permission_overlay_desc),
            granted = permissions.overlay,
        ) { context.startActivity(overlaySettingsIntent(context)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(
                title = stringResource(R.string.permission_notifications),
                description = stringResource(R.string.permission_notifications_desc),
                granted = permissions.notifications,
            ) { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        PermissionRow(
            title = stringResource(R.string.permission_accessibility),
            description = stringResource(R.string.permission_accessibility_desc),
            granted = permissions.accessibility,
        ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        PermissionRow(
            title = stringResource(R.string.permission_write_settings),
            description = stringResource(R.string.permission_write_settings_desc),
            granted = permissions.writeSettings,
        ) { context.startActivity(ItemLauncher.writeSettingsIntent(context)) }
        PermissionRow(
            title = stringResource(R.string.permission_dnd),
            description = stringResource(R.string.permission_dnd_desc),
            granted = permissions.dnd,
        ) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        PermissionRow(
            title = stringResource(R.string.permission_usage),
            description = stringResource(R.string.permission_usage_desc),
            granted = permissions.usage,
        ) { context.startActivity(UsageApps.settingsIntent()) }
        PermissionRow(
            title = stringResource(R.string.permission_battery),
            description = stringResource(R.string.permission_battery_desc),
            granted = permissions.battery,
        ) { context.startActivity(batteryOptimizationIntent(context)) }
        val shizukuStatus = when (permissions.shizuku) {
            ShizukuShell.State.NOT_INSTALLED -> R.string.shizuku_not_installed
            ShizukuShell.State.NOT_RUNNING -> R.string.shizuku_not_running
            ShizukuShell.State.NO_PERMISSION -> R.string.shizuku_no_permission
            ShizukuShell.State.READY -> null
        }
        PermissionRow(
            title = stringResource(R.string.permission_shizuku),
            description = stringResource(R.string.permission_shizuku_desc) +
                shizukuStatus?.let { "\n" + stringResource(it) }.orEmpty(),
            granted = permissions.shizuku == ShizukuShell.State.READY,
        ) {
            when (permissions.shizuku) {
                ShizukuShell.State.NO_PERMISSION -> ShizukuShell.requestPermission(context)
                ShizukuShell.State.NOT_RUNNING -> if (!ShizukuShell.openManager(context)) onChanged()
                else -> try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, SHIZUKU_SITE.toUri()))
                } catch (e: ActivityNotFoundException) {
                    toast(context, R.string.launch_failed)
                }
            }
        }
    }
}

@Composable
private fun TriggerSettings(prefs: Prefs, accessibilityEnabled: Boolean) {
    var mode by remember { mutableStateOf(prefs.triggerMode) }
    SectionTitle(stringResource(R.string.section_trigger_mode))
    Section {
        ChoiceRow(
            stringResource(R.string.setting_trigger_mode),
            listOf(TriggerMode.EDGE to R.string.trigger_edge, TriggerMode.BALL to R.string.trigger_ball),
            mode,
        ) {
            mode = it
            prefs.triggerMode = it
        }
    }
    if (mode == TriggerMode.EDGE) EdgeHandleSettings(prefs) else FloatingBallSettings(prefs, accessibilityEnabled)
}

@Composable
private fun EdgeHandleSettings(prefs: Prefs) {
    SectionTitle(stringResource(R.string.section_position))
    Section {
        var side by remember { mutableStateOf(prefs.side) }
        ChoiceRow(
            stringResource(R.string.setting_side),
            listOf(Side.LEFT to R.string.side_left, Side.RIGHT to R.string.side_right),
            side,
        ) {
            side = it
            prefs.side = it
        }
        SliderRow(stringResource(R.string.setting_handle_position), "%", prefs.handlePositionPercent, 0..100) {
            prefs.handlePositionPercent = it
        }
        Hint(stringResource(R.string.handle_drag_hint))
    }
    SectionTitle(stringResource(R.string.section_area))
    Section {
        // The system grants at most 200dp of back-gesture exclusion per edge.
        SliderRow(stringResource(R.string.setting_handle_height), "dp", prefs.handleHeightDp, 60..200) {
            prefs.handleHeightDp = it
        }
        SliderRow(stringResource(R.string.setting_handle_width), "dp", prefs.handleWidthDp, 8..40) {
            prefs.handleWidthDp = it
        }
        var showHandle by remember { mutableStateOf(prefs.showHandle) }
        SwitchRow(stringResource(R.string.setting_show_handle), showHandle) {
            showHandle = it
            prefs.showHandle = it
        }
        var followFinger by remember { mutableStateOf(prefs.followFinger) }
        SwitchRow(stringResource(R.string.setting_follow_finger), followFinger) {
            followFinger = it
            prefs.followFinger = it
        }
        Hint(stringResource(R.string.follow_finger_hint))
    }
}

@Composable
private fun FloatingBallSettings(prefs: Prefs, accessibilityEnabled: Boolean) {
    SectionTitle(stringResource(R.string.section_ball_look))
    Section {
        var shape by remember { mutableStateOf(prefs.ballShape) }
        ChoiceRow(
            stringResource(R.string.setting_ball_shape),
            listOf(BallShape.CIRCLE to R.string.ball_circle, BallShape.SQUARE to R.string.ball_square),
            shape,
        ) {
            shape = it
            prefs.ballShape = it
        }
        SliderRow(stringResource(R.string.setting_ball_size), "dp", prefs.ballSizeDp, 32..96) { prefs.ballSizeDp = it }
        SliderRow(stringResource(R.string.setting_ball_opacity), "%", prefs.ballOpacityPercent, 30..100) {
            prefs.ballOpacityPercent = it
        }
        Hint(stringResource(R.string.ball_hint))
    }
    SectionTitle(stringResource(R.string.section_ball_actions))
    Section {
        var tapAction by remember { mutableStateOf(prefs.ballTapAction) }
        ActionChoice(stringResource(R.string.setting_ball_tap), tapAction) {
            tapAction = it
            prefs.ballTapAction = it
        }
        Hint(stringResource(R.string.ball_pull_hint))
        var haptics by remember { mutableStateOf(prefs.ballHaptics) }
        SwitchRow(stringResource(R.string.setting_ball_haptics), haptics) {
            haptics = it
            prefs.ballHaptics = it
        }
        Hint(stringResource(R.string.ball_haptics_hint))
    }
    SectionTitle(stringResource(R.string.section_ball_menu))
    Section { BallMenuSettings(prefs) }
    SectionTitle(stringResource(R.string.section_keyboard))
    Section {
        var avoidKeyboard by remember { mutableStateOf(prefs.avoidKeyboard) }
        SwitchRow(stringResource(R.string.setting_avoid_keyboard), avoidKeyboard) {
            avoidKeyboard = it
            prefs.avoidKeyboard = it
        }
        Hint(stringResource(R.string.avoid_keyboard_hint))
        if (!accessibilityEnabled) Hint(stringResource(R.string.needs_accessibility_hint))
    }
}

@Composable
private fun ThemeSettings(prefs: Prefs) {
    SectionTitle(stringResource(R.string.section_theme))
    Section {
        var preset by remember { mutableStateOf(prefs.themePreset) }
        ThemePicker(preset) {
            preset = it
            prefs.themePreset = it
        }
        var darkMode by remember { mutableStateOf(prefs.darkMode) }
        ChoiceRow(
            stringResource(R.string.setting_dark_mode),
            listOf(
                DarkMode.SYSTEM to R.string.dark_mode_system,
                DarkMode.LIGHT to R.string.dark_mode_light,
                DarkMode.DARK to R.string.dark_mode_dark,
            ),
            darkMode,
        ) {
            darkMode = it
            prefs.darkMode = it
        }
        IconPackChoice(prefs)
    }
    SectionTitle(stringResource(R.string.section_panel))
    Section {
        SliderRow(stringResource(R.string.setting_panel_width), "%", prefs.panelWidthPercent, 50..100) {
            prefs.panelWidthPercent = it
        }
        // Blurring what is behind a window needs Android 12.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            var blur by remember { mutableStateOf(prefs.panelBlur) }
            SwitchRow(stringResource(R.string.setting_panel_blur), blur) {
                blur = it
                prefs.panelBlur = it
            }
            Hint(stringResource(R.string.panel_blur_hint))
        }
        var seeThrough by remember { mutableStateOf(prefs.brightnessSeeThrough) }
        SwitchRow(stringResource(R.string.setting_brightness_see_through), seeThrough) {
            seeThrough = it
            prefs.brightnessSeeThrough = it
        }
        SliderRow(stringResource(R.string.setting_scrim), "%", prefs.scrimPercent, 0..80) { prefs.scrimPercent = it }
        Hint(stringResource(R.string.scrim_hint))
    }
    SectionTitle(stringResource(R.string.section_details))
    Section { AppearanceSettings(prefs) }
}

/** Sizes and colors of cards and icons on top of the theme, with a button that puts them all back. */
@Composable
private fun AppearanceSettings(prefs: Prefs) {
    var resets by remember { mutableIntStateOf(0) }
    fun change(transform: (Appearance) -> Appearance) {
        prefs.appearance = transform(prefs.appearance)
    }
    // A reset starts the controls over from the defaults.
    key(resets) {
        val start = remember { prefs.appearance }
        val radius = start.cardRadiusDp ?: prefs.themePreset.cardRadius.value.roundToInt()
        SliderRow(stringResource(R.string.setting_card_radius), "dp", radius, CARD_RADIUS_RANGE) { value ->
            change { it.copy(cardRadiusDp = value) }
        }
        SliderRow(stringResource(R.string.setting_card_gap), "dp", start.cardGapDp, CARD_GAP_RANGE) { value ->
            change { it.copy(cardGapDp = value) }
        }
        SliderRow(stringResource(R.string.setting_panel_padding), "dp", start.panelPaddingDp, PANEL_PADDING_RANGE) { value ->
            change { it.copy(panelPaddingDp = value) }
        }
        SliderRow(stringResource(R.string.setting_card_padding), "dp", start.cardPaddingDp, CARD_PADDING_RANGE) { value ->
            change { it.copy(cardPaddingDp = value) }
        }
        SliderRow(stringResource(R.string.setting_label_size), "sp", start.labelSizeSp, LABEL_SIZE_RANGE) { value ->
            change { it.copy(labelSizeSp = value) }
        }
        ColorChoice(stringResource(R.string.setting_background_color), start.backgroundColor) { color ->
            change { it.copy(backgroundColor = color) }
        }
        Hint(stringResource(R.string.background_color_hint))
        ColorChoice(stringResource(R.string.setting_switch_on_color), start.switchOnColor) { color ->
            change { it.copy(switchOnColor = color) }
        }
        ColorChoice(stringResource(R.string.setting_switch_off_color), start.switchOffColor) { color ->
            change { it.copy(switchOffColor = color) }
        }
    }
    OutlinedButton(
        onClick = {
            prefs.appearance = Appearance()
            resets++
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.appearance_reset))
    }
}

/** A title over one swatch per color to pick, after a chip that follows the theme; it keeps its own selection. */
@Composable
private fun ColorChoice(title: String, initial: String?, onSelect: (String?) -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    fun select(color: String?) {
        selected = color
        onSelect(color)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { select(null) },
                label = { Text(stringResource(R.string.color_follow_theme)) },
            )
            SWATCHES.forEach { color ->
                val chosen = color == selected
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(appearanceColor(color))
                        .border(
                            if (chosen) 3.dp else 1.dp,
                            if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        )
                        .clickable { select(color) },
                )
            }
        }
    }
}

/** Colors offered for the panel's background and switches: light and dark neutrals, soft tints and strong colors. */
private val SWATCHES = listOf(
    "#FFFFFF", "#F2F2F7", "#FFF8E7", "#E8F0FE", "#E6F4EA", "#FCE8F0",
    "#1C1C1E", "#000000", "#0E1024", "#1F2A44",
    "#3E63DD", "#5B47D6", "#34A853", "#F29900", "#E8453C", "#00A3BF",
)

@Composable
private fun AdvancedSettings(prefs: Prefs, accessibilityEnabled: Boolean) {
    val context = LocalContext.current
    SectionTitle(stringResource(R.string.section_app_list))
    Section {
        var mode by remember { mutableStateOf(prefs.appListMode) }
        ChoiceRow(
            stringResource(R.string.setting_app_list_mode),
            listOf(
                AppListMode.BLACKLIST to R.string.app_list_blacklist,
                AppListMode.WHITELIST to R.string.app_list_whitelist,
            ),
            mode,
        ) {
            mode = it
            prefs.appListMode = it
        }
        AppListRow(prefs, mode)
        Hint(stringResource(if (mode == AppListMode.BLACKLIST) R.string.blacklist_hint else R.string.whitelist_hint))
        if (!accessibilityEnabled) Hint(stringResource(R.string.needs_accessibility_hint))
        var hideInLandscape by remember { mutableStateOf(prefs.hideInLandscape) }
        SwitchRow(stringResource(R.string.setting_hide_in_landscape), hideInLandscape) {
            hideInLandscape = it
            prefs.hideInLandscape = it
        }
    }
    SectionTitle(stringResource(R.string.section_other_entries))
    Section {
        Hint(stringResource(R.string.tile_hint))
        OutlinedButton(onClick = { OpenPanelActivity.pinShortcut(context) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.shortcut_pin))
        }
        Hint(stringResource(R.string.shortcut_hint))
    }
    SectionTitle(stringResource(R.string.section_notification))
    Section {
        var showNotification by remember { mutableStateOf(prefs.showNotification) }
        SwitchRow(stringResource(R.string.setting_show_notification), showNotification) {
            showNotification = it
            prefs.showNotification = it
        }
        Hint(stringResource(R.string.show_notification_hint))
    }
}

/** The kept configs, to switch between, add, rename and delete, and the one in use to export; imports add one. */
@Composable
private fun ConfigSettings() {
    val context = LocalContext.current
    val store = remember { ConfigStore.get(context) }
    val profiles by store.profiles.collectAsState()
    val active by store.active.collectAsState()
    val activeName = profiles.firstOrNull { it.id == active }?.name.orEmpty()
    var renaming by remember { mutableStateOf<ConfigProfile?>(null) }
    var deleting by remember { mutableStateOf<ConfigProfile?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        if (it != null) toast(context, exportConfig(context, it, store.config.value))
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) toast(context, importConfig(context, it, store))
    }
    SectionTitle(stringResource(R.string.section_configs))
    Section {
        Hint(stringResource(R.string.configs_hint))
        profiles.forEach { profile ->
            ProfileRow(
                profile = profile,
                active = profile.id == active,
                onSwitch = { store.switchTo(profile.id) },
                onRename = { renaming = profile },
                onDelete = { deleting = profile },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { store.add(context.getString(R.string.config_new_name, profiles.size + 1)) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.config_new))
            }
            OutlinedButton(
                onClick = { store.add(context.getString(R.string.config_copy_name, activeName), store.config.value) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.config_copy))
            }
        }
    }
    SectionTitle(stringResource(R.string.section_config_files))
    Section {
        Hint(stringResource(R.string.config_hint))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportLauncher.launch(exportFileName(activeName)) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.config_export))
            }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.config_import))
            }
        }
    }
    renaming?.let { profile ->
        NameDialog(
            name = profile.name,
            onDone = { name ->
                if (name.isNotEmpty()) store.rename(profile.id, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.config_delete_confirm, profile.name)) },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(profile.id)
                    deleting = null
                }) { Text(stringResource(R.string.config_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** A kept config: a tap switches to it. The one in use cannot be deleted. */
@Composable
private fun ProfileRow(
    profile: ConfigProfile,
    active: Boolean,
    onSwitch: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !active, onClick = onSwitch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = active, onClick = null)
        Text(
            profile.name,
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onRename) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.config_rename))
        }
        // Same width with or without the button, so the rows line up.
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (!active) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.config_delete))
                }
            }
        }
    }
}

/** Asks for a config's new name. */
@Composable
private fun NameDialog(name: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.config_rename)) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onDone(text.trim()) }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun AboutSettings() {
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    Section {
        Text(stringResource(R.string.about_version, stringResource(R.string.app_name), version.orEmpty()))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, PROJECT_URL.toUri()))
                    } catch (e: ActivityNotFoundException) {
                        toast(context, R.string.launch_failed)
                    }
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.about_project), Modifier.weight(1f))
            Text(PROJECT_URL.removePrefix("https://"), color = MaterialTheme.colorScheme.primary)
        }
        Hint(stringResource(R.string.about_credits))
    }
}

private const val PROJECT_URL = "https://github.com/AlstarKnight/SlideX"
private const val SHIZUKU_SITE = "https://shizuku.rikka.app/"

/** A row showing what a gesture does; tapping it lists "open the panel" and every action to pick from. */
@Composable
private fun ActionChoice(title: String, selected: Action?, onSelect: (Action?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { picking = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f))
        Text(actionLabel(selected), color = MaterialTheme.colorScheme.primary)
    }
    if (!picking) return
    // The app drawer lives inside the panel, so it is not offered on its own.
    val options = listOf<Action?>(null) + Action.entries.filter { it != Action.APP_DRAWER }
    AlertDialog(
        onDismissRequest = { picking = false },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(option)
                                picking = false
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(actionLabel(option), Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** The shortcuts a long press on the floating icon shows, one row per direction; a tap on a row picks another. */
@Composable
private fun BallMenuSettings(prefs: Prefs) {
    var menu by remember { mutableStateOf(prefs.ballMenu) }
    var picking by remember { mutableStateOf<Direction?>(null) }
    fun change(new: Map<Direction, Item>) {
        menu = new
        prefs.ballMenu = new
    }
    Hint(stringResource(R.string.ball_menu_hint))
    Direction.entries.forEach { direction ->
        val item = menu[direction]
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { picking = direction },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(direction.labelRes), Modifier.weight(1f))
            Text(
                item?.label ?: stringResource(R.string.ball_menu_empty),
                color = if (item == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Same width with or without the button, so the names line up.
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (item != null) {
                    IconButton(onClick = { change(menu - direction) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ball_menu_clear))
                    }
                }
            }
        }
    }
    val direction = picking ?: return
    Dialog(onDismissRequest = { picking = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AddItemSheet(
            onAdd = { items ->
                items.firstOrNull()?.let { change(menu + (direction to it)) }
                picking = null
            },
            onDismiss = { picking = null },
            // The app drawer lives inside the panel.
            excludedActions = setOf(Action.APP_DRAWER),
        )
    }
}

@get:StringRes
private val Direction.labelRes: Int
    get() = when (this) {
        Direction.UP -> R.string.direction_up
        Direction.DOWN -> R.string.direction_down
        Direction.LEFT -> R.string.direction_left
        Direction.RIGHT -> R.string.direction_right
    }

/** A row naming the icon pack in use; tapping it lists the installed packs. */
@Composable
private fun IconPackChoice(prefs: Prefs) {
    val context = LocalContext.current
    val packs by produceState<List<IconPackEntry>?>(null) {
        value = withContext(Dispatchers.IO) { IconPacks.installed(context) }
    }
    var selected by remember { mutableStateOf(prefs.iconPack) }
    var picking by remember { mutableStateOf(false) }
    val systemIcons = stringResource(R.string.icon_pack_none)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { picking = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.setting_icon_pack), Modifier.weight(1f))
        Text(
            packs?.firstOrNull { it.packageName == selected }?.label ?: systemIcons,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (!picking) return
    val options = listOf<IconPackEntry?>(null) + packs.orEmpty()
    AlertDialog(
        onDismissRequest = { picking = false },
        title = { Text(stringResource(R.string.setting_icon_pack)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = option?.packageName
                                prefs.iconPack = selected
                                InstalledApps.clearIconCache()
                                picking = false
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option?.packageName == selected, onClick = null)
                        Text(option?.label ?: systemIcons, Modifier.padding(start = 12.dp))
                    }
                }
                if (packs?.isEmpty() == true) Hint(stringResource(R.string.icon_pack_empty))
            }
        },
        confirmButton = {
            TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** A row showing how many apps are on the blacklist or whitelist; tapping it opens the app list. */
@Composable
private fun AppListRow(prefs: Prefs, mode: AppListMode) {
    var apps by remember { mutableStateOf(prefs.listedApps) }
    var picking by remember { mutableStateOf(false) }
    val title = stringResource(
        if (mode == AppListMode.BLACKLIST) R.string.setting_blacklist else R.string.setting_whitelist,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { picking = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f))
        Text(
            if (apps.isEmpty()) {
                stringResource(R.string.listed_apps_none)
            } else {
                stringResource(R.string.listed_apps_count, apps.size)
            },
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (picking) {
        AppListDialog(
            title = title,
            selected = apps,
            onDone = {
                apps = it
                prefs.listedApps = it
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun AppListDialog(title: String, selected: Set<String>, onDone: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxHeight(0.85f), shape = MaterialTheme.shapes.extraLarge) {
            AppChecklist(title, selected, onDone, onDismiss)
        }
    }
}

/**
 * Every app with a box to tick, searchable, the [selected] ones first; home screens are listed too, so a setting can
 * apply to (or leave out) the home screen.
 */
@Composable
fun AppChecklist(
    title: String,
    selected: Set<String>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            val all = InstalledApps.load(context) + InstalledApps.load(context, Intent.CATEGORY_HOME)
            // Apps already chosen come first, so they are easy to find and untick.
            all.distinctBy { it.packageName }.sortedBy { it.packageName !in selected }
        }
    }
    var checked by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }
    Column(modifier.padding(vertical = 16.dp)) {
        Text(
            title,
            Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_apps)) },
        )
        val list = apps
        if (list == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val filtered = searchApps(list, query) { it.search }
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.packageName }) { app ->
                    val on = app.packageName in checked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { checked = if (on) checked - app.packageName else checked + app.packageName }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Box(Modifier.padding(horizontal = 12.dp)) {
                            ItemIcon(AppItem(app.label, app.packageName, app.activityName), 32.dp)
                        }
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.End).padding(horizontal = 16.dp)) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = { onDone(checked) }) { Text(stringResource(R.string.done)) }
        }
    }
}

@Composable
private fun actionLabel(action: Action?): String = stringResource(action?.labelRes ?: R.string.ball_action_panel)

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One tile per preset, showing a swatch of its accent. */
@Composable
fun ThemePicker(selected: ThemePreset?, onSelect: (ThemePreset) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemePreset.entries.forEach { preset ->
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .border(
                        if (preset == selected) 2.dp else 1.dp,
                        if (preset == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape,
                    )
                    .clickable { onSelect(preset) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(presetSwatch(preset)),
                )
                Text(
                    stringResource(preset.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A title with one chip per option below it. */
@Composable
fun <T> ChoiceRow(title: String, options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(title)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(stringResource(label)) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A slider over [range] that moves in steps of [step]; it keeps its own position, starting from [initial]. */
@Composable
fun SliderRow(
    title: String,
    unit: String,
    initial: Int,
    range: IntRange,
    step: Int = 1,
    valueText: (Int) -> String = { "$it$unit" },
    onChange: (Int) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    Column {
        Row {
            Text(title, Modifier.weight(1f))
            Text(valueText(value), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = {
                value = (it / step).roundToInt() * step
                onChange(value)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onRequest: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (granted) {
            Text(stringResource(R.string.permission_granted), color = MaterialTheme.colorScheme.primary)
        } else {
            OutlinedButton(onClick = onRequest) { Text(stringResource(R.string.permission_grant)) }
        }
    }
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun overlaySettingsIntent(context: Context) =
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())

@SuppressLint("BatteryLife")
private fun batteryOptimizationIntent(context: Context) =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())

private fun toast(context: Context, message: Int) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** Writes the config and its background pictures to a user-picked file; returns the message to show. */
private fun exportConfig(context: Context, uri: Uri, config: PanelConfig): Int {
    try {
        val stream = context.contentResolver.openOutputStream(uri) ?: return R.string.config_io_failed
        stream.use { out ->
            writeConfigArchive(out, config) { name -> BackgroundImages.file(context, name).takeIf { it.exists() } }
        }
        return R.string.config_exported
    } catch (e: IOException) {
        return R.string.config_io_failed
    }
}

/** Adds the config in a user-picked file as a new one, named after the file, and switches to it. */
private fun importConfig(context: Context, uri: Uri, store: ConfigStore): Int {
    try {
        val stream = context.contentResolver.openInputStream(uri) ?: return R.string.config_io_failed
        val config = stream.use { input ->
            readConfigArchive(input) { name, data -> BackgroundImages.save(context, name, data) }
        }
        val name = displayName(context, uri)?.let(::importedConfigName) ?: context.getString(R.string.config_imported_name)
        store.add(name, config)
        return R.string.config_imported
    } catch (e: IOException) {
        return R.string.config_io_failed
    } catch (e: IllegalArgumentException) {
        return R.string.config_invalid
    } finally {
        // Drops pictures in the file that its config does not use, or all of them when the config was invalid.
        store.deleteUnused()
    }
}

/** The name of a user-picked file, as the file picker shows it. */
private fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
