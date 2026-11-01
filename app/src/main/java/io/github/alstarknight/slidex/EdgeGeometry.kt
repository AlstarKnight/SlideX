package io.github.alstarknight.slidex

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class Side { LEFT, RIGHT }

/** True when a drag that started on the edge handle has moved far enough into the screen, mostly horizontally. */
fun isOpenSwipe(side: Side, dx: Float, dy: Float, thresholdPx: Float): Boolean {
    val inward = if (side == Side.LEFT) dx else -dx
    return inward >= thresholdPx && inward > abs(dy)
}

/** Top of the handle so its center sits at [positionPercent] of the screen height, kept fully on screen. */
fun handleTop(screenHeight: Int, handleHeight: Int, positionPercent: Int): Int {
    val center = screenHeight * positionPercent / 100
    return (center - handleHeight / 2).coerceIn(0, (screenHeight - handleHeight).coerceAtLeast(0))
}

/** Inverse of [handleTop]: where the handle's center is, in percent of the screen height. */
fun handlePositionPercent(screenHeight: Int, handleHeight: Int, top: Int): Int =
    if (screenHeight <= 0) 50 else ((top + handleHeight / 2f) * 100 / screenHeight).roundToInt().coerceIn(0, 100)

/** How far out (0 to 1) a panel [panelWidth] wide on [side] is when its inner edge is under a finger at [x]. */
fun panelFraction(side: Side, x: Float, screenWidth: Int, panelWidth: Float): Float {
    if (panelWidth <= 0f) return 1f
    val shown = if (side == Side.LEFT) x else screenWidth - x
    return (shown / panelWidth).coerceIn(0f, 1f)
}

/**
 * Whether a panel let go at [fraction] (0 hidden, 1 fully out) settles open. [velocity] is how fast it was moving
 * out, in pixels per second (negative when moving in): a flick faster than [flingVelocity] decides, otherwise the
 * panel opens when it is at least half out.
 */
fun settlesOpen(fraction: Float, velocity: Float, flingVelocity: Float): Boolean = when {
    velocity >= flingVelocity -> true
    velocity <= -flingVelocity -> false
    else -> fraction >= 0.5f
}

/** The side edge nearer to a point at [x], e.g. the center of the floating icon or a finger dragging the handle. */
fun nearestSide(x: Float, screenWidth: Int): Side = if (x < screenWidth / 2f) Side.LEFT else Side.RIGHT

/** Offset of something [size] long whose center sits at [fraction] (0 to 1) of [length], kept fully inside it. */
fun centeredOffset(length: Int, size: Int, fraction: Float): Int =
    (length * fraction - size / 2f).roundToInt().coerceIn(0, (length - size).coerceAtLeast(0))

/** Inverse of [centeredOffset]: where the center is, as a fraction of [length]. */
fun centerFraction(length: Int, size: Int, offset: Int): Float =
    if (length <= 0) 0.5f else ((offset + size / 2f) / length).coerceIn(0f, 1f)

/**
 * Top of something [size] tall placed at [top], moved up to end [gap] above a keyboard whose top edge is at
 * [keyboardTop] when it would otherwise overlap it. Null [keyboardTop] means no keyboard is shown.
 */
fun aboveKeyboard(top: Int, size: Int, keyboardTop: Int?, gap: Int): Int =
    if (keyboardTop == null || top + size + gap <= keyboardTop) top else (keyboardTop - size - gap).coerceAtLeast(0)

/**
 * Which of the floating icon's shortcuts a finger that moved [dx], [dy] since its long press points at: the way it
 * moved most, once it moved at least [thresholdPx]; null before that.
 */
fun menuDirection(dx: Float, dy: Float, thresholdPx: Float): Direction? = when {
    hypot(dx, dy) < thresholdPx -> null
    abs(dx) >= abs(dy) -> if (dx < 0) Direction.LEFT else Direction.RIGHT
    else -> if (dy < 0) Direction.UP else Direction.DOWN
}

/**
 * Where along a [length] the center of the floating icon's menu goes: on the icon at [position], moved inward as
 * needed so the shortcuts, which reach [reach] from the center, stay inside.
 */
fun menuCenter(position: Float, reach: Float, length: Float): Float =
    if (length < 2 * reach) length / 2 else position.coerceIn(reach, length - reach)
