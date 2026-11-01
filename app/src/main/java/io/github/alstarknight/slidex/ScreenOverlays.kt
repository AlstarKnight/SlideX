package io.github.alstarknight.slidex

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Overlays that only need the draw-over-apps permission: a warm eye-care filter over the whole screen, and invisible
 * windows that keep the screen awake or turn it to landscape or portrait, whatever the app in front asks for.
 */
class ScreenOverlays(context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val eyeCareView = View(context).apply { setBackgroundColor(EYE_CARE_COLOR) }
    private val keepAwakeView = View(context)
    private val orientationView = View(context)

    var eyeCare = false
        set(value) {
            if (field == value) return
            field = value
            if (value) windowManager.addView(eyeCareView, eyeCareParams()) else windowManager.removeView(eyeCareView)
        }

    var keepAwake = false
        set(value) {
            if (field == value) return
            field = value
            if (value) windowManager.addView(keepAwakeView, keepAwakeParams()) else windowManager.removeView(keepAwakeView)
        }

    /** The orientation forced on the screen, as an [ActivityInfo] screen orientation, or null to leave it to apps. */
    var orientation: Int? = null
        set(value) {
            if (field == value) return
            val shown = field != null
            field = value
            when {
                value == null -> windowManager.removeView(orientationView)
                shown -> windowManager.updateViewLayout(orientationView, orientationParams(value))
                else -> windowManager.addView(orientationView, orientationParams(value))
            }
        }

    fun release() {
        eyeCare = false
        keepAwake = false
        orientation = null
    }

    private fun eyeCareParams(): WindowManager.LayoutParams {
        // Big enough for either orientation, so a rotation never uncovers part of the screen.
        val screen = windowManager.screenSize()
        val side = maxOf(screen.x, screen.y)
        return passThroughParams(side, side, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
            }
        }
    }

    private fun keepAwakeParams() = passThroughParams(1, 1, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    // An overlay's orientation wins over the app's below it, like the original app's forced landscape and portrait.
    private fun orientationParams(orientation: Int) =
        passThroughParams(1, 1, 0).apply { screenOrientation = orientation }

    private fun passThroughParams(width: Int, height: Int, extraFlags: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            extraFlags,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        // Android 12+ blocks touches that pass through another app's overlay unless it is at most 80% opaque.
        alpha = MAX_PASS_THROUGH_ALPHA
    }

    private companion object {
        const val MAX_PASS_THROUGH_ALPHA = 0.8f
        val EYE_CARE_COLOR = Color.argb(72, 255, 140, 0)
    }
}
