package io.github.alstarknight.slidex

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Foreground service that keeps the edge handle or the floating icon on screen and opens the panel from it. */
class PanelService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var windowManager: WindowManager
    private lateinit var launcher: ItemLauncher
    private var handle: EdgeHandleView? = null
    private var ball: FloatingBall? = null
    private var panel: PanelWindow? = null
    private val scope = MainScope()

    /** Where the edge handle is being dragged to: its side and top, saved when it is let go. */
    private var handleDrag: Pair<Side, Int>? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        windowManager = getSystemService(WindowManager::class.java)
        launcher = ItemLauncher(this)
        // Add the overlay before going foreground: Android 15 only lets an overlay app start a
        // foreground service from the background while one of its overlay windows is visible.
        if (Settings.canDrawOverlays(this)) addTrigger()
        prefs.prefs.registerOnSharedPreferenceChangeListener(this)
        scope.launch { ActionAccessibilityService.foregroundPackage.collect { updateTriggerVisibility() } }
        scope.launch { ActionAccessibilityService.keyboardTop.collect { updateKeyboard() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (prefs.showNotification && !startInForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (handle == null && ball == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_OPEN_PANEL) {
            openPanel(ball?.side ?: prefs.side, intent.getIntExtra(EXTRA_PAGE, -1).takeIf { it >= 0 })
        }
        return START_STICKY
    }

    private fun startInForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        true
    } catch (e: IllegalStateException) {
        Log.w(TAG, "Cannot start in foreground", e)
        false
    }

    override fun onDestroy() {
        scope.cancel()
        prefs.prefs.unregisterOnSharedPreferenceChangeListener(this)
        panel?.remove()
        removeTrigger()
        launcher.release()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateHandle()
        ball?.update()
        updateTriggerVisibility()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == Prefs.KEY_SHOW_NOTIFICATION) {
            if (prefs.showNotification) {
                startInForeground()
            } else {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            }
            return
        }
        if ((prefs.triggerMode == TriggerMode.BALL) != (ball != null)) {
            removeTrigger()
            addTrigger()
        } else {
            updateHandle()
            ball?.update()
            updateKeyboard()
            updateTriggerVisibility()
        }
    }

    private fun addTrigger() {
        when (prefs.triggerMode) {
            TriggerMode.EDGE -> addHandle()
            TriggerMode.BALL -> ball = FloatingBall(
                this,
                windowManager,
                prefs,
                onTap = ::onBallTap,
                onPullStart = { side, rawX -> openPanel(side, heldAt = rawX) },
                onPull = { rawX -> panel?.drag(rawX) },
                onPullEnd = ::releasePanel,
                onPick = { item -> launcher.launch(item) },
            ).also { it.add() }
        }
        updateKeyboard()
        updateTriggerVisibility()
    }

    private fun updateKeyboard() {
        ball?.keyboardTop = ActionAccessibilityService.keyboardTop.value.takeIf { prefs.avoidKeyboard }
    }

    /**
     * Shows the trigger unless the panel is open, the app list rules out the app in front, or it is landscape. A
     * landscape forced from the panel keeps it, so the panel can still be opened to undo it.
     */
    private fun updateTriggerVisibility() {
        val front = ActionAccessibilityService.foregroundPackage.value
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        // The handle stays while a finger drags the panel out from it: hiding it would cancel the touch.
        val visible = panel?.held != false &&
            triggerAllowedIn(front, prefs.appListMode, prefs.listedApps, packageName) &&
            !(landscape && prefs.hideInLandscape && !launcher.orientationForced)
        handle?.visibility = if (visible) View.VISIBLE else View.GONE
        ball?.visible = visible
    }

    /** A tap on the floating icon opens the panel, or runs the action chosen for it. */
    private fun onBallTap(side: Side) {
        val action = prefs.ballTapAction
        if (action == null) openPanel(side) else launcher.launch(ActionItem(getString(action.labelRes), action))
    }

    /** Lets go of a panel pulled out by a finger, moving sideways at [velocityX]. */
    private fun releasePanel(velocityX: Float) {
        panel?.release(velocityX)
        updateTriggerVisibility()
    }

    private fun removeTrigger() {
        handle?.let { windowManager.removeView(it) }
        handle = null
        ball?.remove()
        ball = null
    }

    private fun addHandle() {
        val view = EdgeHandleView(
            this,
            onOpen = { rawX -> openPanel(prefs.side, heldAt = rawX.takeIf { prefs.followFinger }) },
            onDrag = { rawX -> panel?.drag(rawX) },
            onRelease = ::releasePanel,
            onMove = ::moveHandle,
            onMoveEnd = ::dropHandle,
        )
        handle = view
        applyHandleStyle(view)
        windowManager.addView(view, handleLayoutParams())
    }

    /** Follows a long-press drag of the handle: up and down, and over to whichever side the finger is nearer. */
    private fun moveHandle(rawX: Float, rawY: Float) {
        val view = handle ?: return
        val screen = windowManager.screenSize()
        val params = handleLayoutParams()
        val side = nearestSide(rawX, screen.x)
        params.gravity = Gravity.TOP or if (side == Side.LEFT) Gravity.LEFT else Gravity.RIGHT
        params.y = (rawY - params.height / 2f).roundToInt().coerceIn(0, (screen.y - params.height).coerceAtLeast(0))
        view.side = side
        windowManager.updateViewLayout(view, params)
        handleDrag = side to params.y
    }

    private fun dropHandle() {
        val (side, top) = handleDrag ?: return
        handleDrag = null
        val height = handleLayoutParams().height
        prefs.setHandlePlacement(side, handlePositionPercent(windowManager.screenSize().y, height, top))
    }

    private fun updateHandle() {
        val view = handle ?: return
        if (handleDrag != null) return
        applyHandleStyle(view)
        windowManager.updateViewLayout(view, handleLayoutParams())
    }

    private fun applyHandleStyle(view: EdgeHandleView) {
        view.side = prefs.side
        view.showStrip = prefs.showHandle
    }

    private fun handleLayoutParams(): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val width = (prefs.handleWidthDp * density).toInt()
        val height = (prefs.handleHeightDp * density).toInt()
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or if (prefs.side == Side.LEFT) Gravity.LEFT else Gravity.RIGHT
            y = handleTop(windowManager.screenSize().y, height, prefs.handlePositionPercent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /**
     * Opens the panel on [page], or by default on the page whose scene lists the app in front; with [heldAt] it
     * follows a finger at that screen x instead of sliding out.
     */
    private fun openPanel(side: Side, page: Int? = null, heldAt: Float? = null) {
        if (panel != null || (handle == null && ball == null)) return
        val store = ConfigStore.get(this)
        panel = PanelWindow(
            context = this,
            windowManager = windowManager,
            side = side,
            initialPage = page ?: store.config.value.scenePage(ActionAccessibilityService.foregroundPackage.value),
            widthFraction = prefs.panelWidthPercent / 100f,
            scrimAlpha = prefs.scrimPercent / 100f,
            blurBehind = prefs.panelBlur,
            seeThroughOnBrightness = prefs.brightnessSeeThrough,
            store = store,
            launcher = launcher,
            onOpenSettings = {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            onClosed = {
                panel = null
                updateTriggerVisibility()
            },
        ).also { it.show(heldAt) }
        updateTriggerVisibility()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val openPanel = PendingIntent.getService(
            this,
            0,
            Intent(this, PanelService::class.java).setAction(ACTION_OPEN_PANEL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_panel)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openPanel)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "PanelService"
        private const val CHANNEL_ID = "service"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_OPEN_PANEL = "io.github.alstarknight.slidex.OPEN_PANEL"
        private const val EXTRA_PAGE = "page"

        fun start(context: Context) {
            val intent = Intent(context, PanelService::class.java)
            try {
                if (Prefs(context).showNotification) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: IllegalStateException) {
                // Android can refuse to start services from the background; opening the app starts it again.
                Log.w(TAG, "Cannot start the service", e)
            }
        }

        /** Opens the panel on [page], e.g. to show a card that was just added outside it. */
        fun openPanel(context: Context, page: Int) {
            if (!Prefs(context).serviceEnabled) return
            val intent = Intent(context, PanelService::class.java).setAction(ACTION_OPEN_PANEL).putExtra(EXTRA_PAGE, page)
            try {
                context.startService(intent)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Cannot open the panel", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PanelService::class.java))
        }
    }
}
