package io.github.alstarknight.slidex

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlin.math.hypot
import kotlin.math.roundToInt

/** What a touch on the floating icon became once the finger moved or was held. */
private enum class BallGesture { MOVE, PULL, MENU }

/**
 * Floating icon that can be dragged anywhere. A tap runs [onTap]; a sideways swipe into the screen pulls the panel
 * out ([onPullStart] with the finger's screen x, then [onPull], and [onPullEnd] with its sideways speed); a long press
 * shows the shortcuts around the icon, and [onPick] runs the one picked. Left alone for a while, it can fade or tuck
 * half into the edge, as set in [Prefs.ballIdle].
 */
class FloatingBall(
    context: Context,
    private val windowManager: WindowManager,
    private val prefs: Prefs,
    private val onTap: (side: Side) -> Unit,
    private val onPullStart: (side: Side, rawX: Float) -> Unit,
    private val onPull: (rawX: Float) -> Unit,
    private val onPullEnd: (velocityX: Float) -> Unit,
    onPick: (Item) -> Unit,
) {
    private val density = context.resources.displayMetrics.density
    private val view = BallView(
        context,
        onPress = ::onPress,
        onMoveStart = ::onMoveStart,
        onMove = ::onMove,
        onRelease = ::onRelease,
    )
    private val menu = BallMenu(context, windowManager, onPick)
    private var gesture: BallGesture? = null

    /** Whether the icon has been left alone long enough to fade or tuck in. */
    private var idle = false
    private var pressed = false
    private val goIdle = Runnable { if (pressed || menu.shown) scheduleIdle() else setIdle(true) }
    private val params = WindowManager.LayoutParams(
        0,
        0,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // No limits, so a tucked-in icon can reach past the screen's edge.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
    private var dragStartX = 0
    private var dragStartY = 0

    init {
        view.setOnClickListener {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onTap(side)
        }
        // Handling the long press also gives it the system's long-press vibration.
        view.setOnLongClickListener { showMenu() }
    }

    var visible: Boolean
        get() = view.visibility == View.VISIBLE
        set(value) {
            // Each time it comes back it shows in full first.
            if (value && !visible) wake()
            view.visibility = if (value) View.VISIBLE else View.GONE
            if (!value) menu.hide()
        }

    /** Top edge of the on-screen keyboard, or null when none is shown; the icon moves up out of its way. */
    var keyboardTop: Int? = null
        set(value) {
            if (field == value) return
            field = value
            update()
        }

    /** The edge the icon is nearer to; the panel slides in from there. */
    val side: Side
        get() = nearestSide(params.x + params.width / 2f, windowManager.screenSize().x)

    fun add() {
        applyPrefs()
        windowManager.addView(view, params)
        scheduleIdle()
    }

    /** Re-reads size, shape, opacity and position, e.g. after a settings change or a rotation. */
    fun update() {
        if (gesture == BallGesture.MOVE) return
        applyPrefs()
        windowManager.updateViewLayout(view, params)
        // The idle look may have just been turned on, so the wait starts over.
        if (!idle) scheduleIdle()
    }

    fun remove() {
        view.removeCallbacks(goIdle)
        menu.hide()
        windowManager.removeView(view)
    }

    private fun applyPrefs() {
        val size = (prefs.ballSizeDp * density).roundToInt()
        val screen = windowManager.screenSize()
        val idleLook = prefs.ballIdle
        params.width = size
        params.height = size
        params.x = centeredOffset(screen.x, size, prefs.ballX)
        // An icon that tucks in keeps to the nearer side edge.
        if (idleLook == BallIdle.TUCK) params.x = edgeOffset(screen.x, size, params.x, tucked = idle)
        val top = centeredOffset(screen.y, size, prefs.ballY)
        params.y = aboveKeyboard(top, size, keyboardTop, (KEYBOARD_GAP_DP * density).roundToInt())
        view.shape = prefs.ballShape
        view.alpha = prefs.ballOpacityPercent / 100f * if (idle && idleLook == BallIdle.FADE) IDLE_ALPHA else 1f
        view.isHapticFeedbackEnabled = prefs.ballHaptics
    }

    /** A finger on the icon brings it back in full; once it lifts, the icon goes idle again after a while. */
    private fun onPress(pressed: Boolean) {
        this.pressed = pressed
        if (pressed) setIdle(false) else scheduleIdle()
    }

    private fun wake() {
        setIdle(false)
        scheduleIdle()
    }

    private fun scheduleIdle() {
        view.removeCallbacks(goIdle)
        if (prefs.ballIdle != BallIdle.NONE) view.postDelayed(goIdle, IDLE_DELAY_MS)
    }

    private fun setIdle(value: Boolean) {
        if (idle == value) return
        idle = value
        update()
    }

    /** Shows the shortcuts around the icon; returns false when none is set. */
    private fun showMenu(): Boolean {
        val items = prefs.ballMenu
        if (items.isEmpty()) return false
        menu.show(items, params.x + params.width / 2f, params.y + params.height / 2f, params.width)
        gesture = BallGesture.MENU
        return true
    }

    /** The finger left the icon: a sideways swipe into the screen pulls the panel out, anything else moves the icon. */
    private fun onMoveStart(dx: Float, dy: Float, rawX: Float) {
        val from = side
        gesture = if (isOpenSwipe(from, dx, dy, thresholdPx = 0f)) {
            onPullStart(from, rawX)
            BallGesture.PULL
        } else {
            dragStartX = params.x
            dragStartY = params.y
            BallGesture.MOVE
        }
    }

    private fun onMove(dx: Float, dy: Float, rawX: Float) {
        when (gesture) {
            BallGesture.MOVE -> {
                val screen = windowManager.screenSize()
                params.x = (dragStartX + dx.roundToInt()).coerceIn(0, (screen.x - params.width).coerceAtLeast(0))
                params.y = (dragStartY + dy.roundToInt()).coerceIn(0, (screen.y - params.height).coerceAtLeast(0))
                windowManager.updateViewLayout(view, params)
            }
            BallGesture.PULL -> onPull(rawX)
            BallGesture.MENU -> menu.select(menuDirection(dx, dy, MENU_SELECT_DP * density))
            null -> Unit
        }
    }

    private fun onRelease(dx: Float, dy: Float, velocityX: Float) {
        when (gesture) {
            // Remember where the icon was dropped, relative to the screen size so it survives rotation.
            BallGesture.MOVE -> {
                val screen = windowManager.screenSize()
                if (prefs.ballIdle == BallIdle.TUCK) {
                    params.x = edgeOffset(screen.x, params.width, params.x, tucked = false)
                    windowManager.updateViewLayout(view, params)
                }
                prefs.setBallPosition(
                    centerFraction(screen.x, params.width, params.x),
                    centerFraction(screen.y, params.height, params.y),
                )
            }
            BallGesture.PULL -> onPullEnd(velocityX)
            // Let go on a shortcut runs it; anywhere else the menu stays open for a tap.
            BallGesture.MENU -> {
                menu.select(null)
                menu.pick(menuDirection(dx, dy, MENU_SELECT_DP * density))
            }
            null -> Unit
        }
        gesture = null
    }
}

private const val KEYBOARD_GAP_DP = 12

/** How long the icon is left alone before it fades or tucks in. */
private const val IDLE_DELAY_MS = 3000L

/** How much of its opacity a faded icon keeps. */
private const val IDLE_ALPHA = 0.4f

/** How far a finger held on the icon moves toward a shortcut to pick it. */
private const val MENU_SELECT_DP = 24

/** Size of the whole screen in pixels, system bars included. */
fun WindowManager.screenSize(): Point =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        currentWindowMetrics.bounds.let { Point(it.width(), it.height()) }
    } else {
        @Suppress("DEPRECATION")
        Point().also { defaultDisplay.getRealSize(it) }
    }

/**
 * The app icon clipped to a circle or a rounded square. Every touch reports [onPress] when it starts and ends. Once
 * the finger moves past the touch slop it reports [onMoveStart] and then every [onMove], and [onRelease] with the
 * sideways speed when it lifts; after a long press it reports moves and the release without [onMoveStart]. A touch
 * that does neither is a click.
 */
@SuppressLint("ViewConstructor")
private class BallView(
    context: Context,
    private val onPress: (pressed: Boolean) -> Unit,
    private val onMoveStart: (dx: Float, dy: Float, rawX: Float) -> Unit,
    private val onMove: (dx: Float, dy: Float, rawX: Float) -> Unit,
    private val onRelease: (dx: Float, dy: Float, velocityX: Float) -> Unit,
) : View(context) {

    var shape = BallShape.CIRCLE
        set(value) {
            field = value
            updateClip()
            invalidate()
        }

    private val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
    private val clip = Path()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var longPressed = false
    private var velocityTracker: VelocityTracker? = null
    private val longPress = Runnable { longPressed = performLongClick() }

    init {
        contentDescription = context.getString(R.string.ball_description)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateClip()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Keep the system back gesture from taking swipes that start on an icon at the edge.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
        }
    }

    private fun updateClip() {
        val w = width.toFloat()
        val h = height.toFloat()
        clip.reset()
        if (shape == BallShape.CIRCLE) {
            clip.addOval(0f, 0f, w, h, Path.Direction.CW)
        } else {
            clip.addRoundRect(0f, 0f, w, h, w * SQUARE_CORNER, h * SQUARE_CORNER, Path.Direction.CW)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val drawable = icon ?: return
        canvas.save()
        canvas.clipPath(clip)
        if (drawable is AdaptiveIconDrawable) {
            // The layers are 108dp with the visible 72dp in the middle, so they overhang a quarter on each side.
            val inset = width / 4
            for (layer in listOfNotNull(drawable.background, drawable.foreground)) {
                layer.setBounds(-inset, -inset, width + inset, height + inset)
                layer.draw(canvas)
            }
        } else {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            velocityTracker?.recycle()
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                moved = false
                longPressed = false
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                onPress(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!moved && !longPressed && hypot(dx, dy) > touchSlop) {
                    removeCallbacks(longPress)
                    moved = true
                    onMoveStart(dx, dy, event.rawX)
                }
                if (moved || longPressed) onMove(dx, dy, event.rawX)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                val tracker = velocityTracker
                velocityTracker = null
                val up = event.actionMasked == MotionEvent.ACTION_UP
                when {
                    // A cancelled touch picks nothing and lets the panel settle where it is.
                    (moved || longPressed) && !up -> onRelease(0f, 0f, 0f)
                    moved || longPressed -> {
                        tracker?.computeCurrentVelocity(1000)
                        onRelease(event.rawX - downX, event.rawY - downY, tracker?.xVelocity ?: 0f)
                    }
                    up -> performClick()
                }
                tracker?.recycle()
                onPress(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private companion object {
        /** Corner radius of the square shape, as a fraction of the icon size. */
        const val SQUARE_CORNER = 0.22f
    }
}
