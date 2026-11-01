package io.github.alstarknight.slidex

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

/**
 * The floating icon's long-press menu: up to four shortcuts around the icon, one per direction. A finger still on
 * the icon picks one by sliding toward it and letting go; otherwise the menu stays, and a tap picks a shortcut or
 * closes it.
 */
class BallMenu(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onPick: (Item) -> Unit,
) {
    private var view: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null
    private var items: Map<Direction, Item> = emptyMap()
    private var selected by mutableStateOf<Direction?>(null)

    /** Shows [items] around an icon [iconSize] pixels wide whose center is at screen [x], [y]. */
    fun show(items: Map<Direction, Item>, x: Float, y: Float, iconSize: Int) {
        if (view != null) return
        this.items = items
        selected = null
        val owner = OverlayLifecycleOwner().also { it.start() }
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                AppTheme {
                    MenuContent(items, x, y, iconSize, selected, onPick = { pick(it) }, onDismiss = ::hide)
                }
            }
        }
        windowManager.addView(view, layoutParams())
        this.view = view
        this.owner = owner
    }

    /** Highlights the shortcut a finger points at, or none. */
    fun select(direction: Direction?) {
        selected = direction
    }

    /** Closes the menu and runs the shortcut in [direction]; returns false, leaving the menu open, when there is none. */
    fun pick(direction: Direction?): Boolean {
        val item = direction?.let(items::get) ?: return false
        hide()
        onPick(item)
        return true
    }

    fun hide() {
        val view = view ?: return
        this.view = null
        windowManager.removeView(view)
        owner?.destroy()
        owner = null
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setFitInsetsTypes(0)
        }
    }
}

/** The shortcuts around the icon at screen [x], [y], over a light scrim that closes the menu when tapped. */
@Composable
private fun MenuContent(
    items: Map<Direction, Item>,
    x: Float,
    y: Float,
    iconSize: Int,
    selected: Direction?,
    onPick: (Direction) -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    // Where the window is on the screen, as the icon's position is given in screen coordinates.
    var bounds by remember { mutableStateOf<IntRect?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .onGloballyPositioned { coordinates ->
                val location = IntArray(2).also(view::getLocationOnScreen)
                bounds = IntRect(IntOffset(location[0], location[1]), coordinates.size)
            },
    ) {
        val area = bounds ?: return@Box
        val bubble = with(density) { BUBBLE_SIZE.toPx() }
        val labelWidth = with(density) { LABEL_WIDTH.toPx() }
        val distance = iconSize / 2f + with(density) { BUBBLE_GAP.toPx() } + bubble / 2
        // Room for a whole shortcut, its name included, on each side of the menu's center.
        val reach = distance + with(density) { (BUBBLE_SIZE / 2 + LABEL_HEIGHT).toPx() }
        val centerX = menuCenter(x - area.left, reach, area.width.toFloat())
        val centerY = menuCenter(y - area.top, reach, area.height.toFloat())
        items.forEach { (direction, item) ->
            val (bubbleX, bubbleY) = when (direction) {
                Direction.UP -> centerX to centerY - distance
                Direction.DOWN -> centerX to centerY + distance
                Direction.LEFT -> centerX - distance to centerY
                Direction.RIGHT -> centerX + distance to centerY
            }
            Shortcut(
                item = item,
                selected = direction == selected,
                onClick = { onPick(direction) },
                modifier = Modifier.offset {
                    IntOffset((bubbleX - labelWidth / 2).roundToInt(), (bubbleY - bubble / 2).roundToInt())
                },
            )
        }
    }
}

@Composable
private fun Shortcut(item: Item, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (selected) SELECTED_SCALE else 1f, label = "shortcutScale")
    Column(
        modifier
            .width(LABEL_WIDTH)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(BUBBLE_SIZE)
                .clip(CircleShape)
                .background(if (selected) colors.primaryContainer else colors.surfaceContainerHighest)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            ItemIcon(item, ICON_SIZE)
        }
        Text(
            item.label,
            Modifier
                .padding(top = 4.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            color = colors.onSurface,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val SCRIM_ALPHA = 0.25f
private const val SELECTED_SCALE = 1.15f
private val BUBBLE_SIZE = 52.dp
private val ICON_SIZE = 32.dp
private val BUBBLE_GAP = 16.dp
private val LABEL_WIDTH = 80.dp
private val LABEL_HEIGHT = 24.dp
