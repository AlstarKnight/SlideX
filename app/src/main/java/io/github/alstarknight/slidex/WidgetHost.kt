package io.github.alstarknight.slidex

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The panel's host for other apps' home-screen widgets; listens for updates only while the panel is open. */
object WidgetHost {
    private const val HOST_ID = 1

    @Volatile
    private var host: AppWidgetHost? = null

    fun get(context: Context): AppWidgetHost =
        host ?: synchronized(this) {
            host ?: AppWidgetHost(context.applicationContext, HOST_ID).also { host = it }
        }

    /** Frees widget ids no card in [configs] uses any more, e.g. after deleting a card, a page or a config. */
    fun deleteUnused(context: Context, configs: List<PanelConfig>) {
        val used = configs.flatMap { it.widgetIds() }.toSet()
        val host = get(context)
        host.appWidgetIds.filterNot { it in used }.forEach(host::deleteAppWidgetId)
    }

    /** Card height for a new widget: its minimum number of home-screen rows, at [ROW_DP] each. */
    fun defaultHeightDp(context: Context, info: AppWidgetProviderInfo): Int {
        val minDp = info.minHeight / context.resources.displayMetrics.density
        return (cells(minDp) * ROW_DP).coerceIn(WIDGET_HEIGHT_RANGE)
    }

    /** Home-screen cells a widget needs, from Android's sizing rule of 70 × n − 30 dp for n cells. */
    fun cells(minDp: Float): Int = ceil((minDp + 30) / 70).toInt().coerceAtLeast(1)

    private const val ROW_DP = 90
}

/** Tells the widget how much room it has, so it can pick a fitting layout. */
fun AppWidgetHostView.updateSize(widthDp: Float, heightDp: Float) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
    } else {
        val width = widthDp.roundToInt()
        val height = heightDp.roundToInt()
        @Suppress("DEPRECATION")
        updateAppWidgetSize(Bundle(), width, height, width, height)
    }
}

/** An installed widget as offered in the picker. */
class WidgetOption(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val columns: Int,
    val rows: Int,
    val preview: ImageBitmap?,
    /** Finds the widget by its app's name and its own, e.g. "微信付款码". */
    val search: SearchKey,
)

/** Every installed widget with its preview image, sorted by app. Call off the main thread. */
fun loadWidgetOptions(context: Context, previewSizePx: Int): List<WidgetOption> {
    val manager = AppWidgetManager.getInstance(context)
    val pm = context.packageManager
    val density = context.resources.displayMetrics.density
    val densityDpi = context.resources.displayMetrics.densityDpi
    val collator = Collator.getInstance()
    return manager.installedProviders
        .map { info ->
            val preview = info.loadPreviewImage(context, densityDpi) ?: info.loadIcon(context, densityDpi)
            val label = info.loadLabel(pm)
            val appName = appLabel(pm, info.provider.packageName)
            WidgetOption(
                info = info,
                label = label,
                appLabel = appName,
                columns = WidgetHost.cells(info.minWidth / density),
                rows = WidgetHost.cells(info.minHeight / density),
                preview = preview?.scaledTo(previewSizePx),
                search = searchKey(appName + label, info.provider.packageName, Pinyin::syllable),
            )
        }
        .sortedWith { a, b -> collator.compare(a.appLabel, b.appLabel).takeIf { it != 0 } ?: collator.compare(a.label, b.label) }
}

private fun appLabel(pm: PackageManager, packageName: String): String =
    try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

/** Bitmap of the drawable fitted into a square of [maxPx], keeping its aspect ratio. */
private fun Drawable.scaledTo(maxPx: Int): ImageBitmap {
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val scale = maxPx.toFloat() / maxOf(width, height)
    return toBitmap(
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
    ).asImageBitmap()
}
