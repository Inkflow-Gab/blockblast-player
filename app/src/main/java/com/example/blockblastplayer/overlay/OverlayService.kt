package com.example.blockblastplayer.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.blockblastplayer.MainActivity
import com.example.blockblastplayer.R
import com.example.blockblastplayer.solver.BlockBlastSolver
import com.example.blockblastplayer.vision.BoardReader
import com.example.blockblastplayer.vision.CalibrationStore
import com.example.blockblastplayer.vision.ScreenCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The floating-window service: capture -> detect -> solve -> draw.
 *
 * Runs as a foreground service because `MediaProjection` requires one on
 * Android 14, and because a service is the only thing allowed to own a
 * `SYSTEM_ALERT_WINDOW` overlay that outlives the activity.
 *
 * **This service only ever reads the screen and draws on top of it.** It never
 * reads touch input from the game and never taps for the user; placing the
 * three pieces stays entirely with the human.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var calibrationStore: CalibrationStore
    private var capture: ScreenCapture? = null

    private var ghostView: GhostOverlayView? = null
    private var bubble: FloatingButtonView? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private var autoJob: Job? = null
    private var analyzing = false

    /** Set by [startOverlay] when the service is re-created with new intent data. */
    private var captureResultCode: Int = 0
    private var captureResultData: Intent? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        calibrationStore = CalibrationStore(this)
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                captureResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                captureResultData = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                ensureViews()
                startCapture()
            }
            ACTION_STOP -> {
                stopEverything()
                stopSelf()
            }
            ACTION_ANALYZE -> {
                ensureViews()
                runAnalysis(manual = true)
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        // A dedicated, low-importance channel so the notification never makes
        // a sound and never appears in the status bar as urgent.
        val channelId = CHANNEL_ID
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.overlay_channel_description)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val analyzeIntent = PendingIntent.getService(
            this, 2,
            Intent(this, OverlayService::class.java).setAction(ACTION_ANALYZE),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_solver)
            .setColor(getColor(R.color.solver_accent))
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_stat_solver, getString(R.string.overlay_analyze), analyzeIntent)
            .addAction(R.drawable.ic_stat_solver, getString(R.string.overlay_stop), stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureViews() {
        if (ghostView != null) return

        ghostView = GhostOverlayView(this).apply {
            // The ghost layer is purely decorative: it must never swallow a tap.
            isClickable = false
            isFocusable = false
        }
        windowManager.addView(
            ghostView,
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // NOT_TOUCHABLE is the important part: every tap passes
                // straight through to the game underneath.
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
        )

        bubble = FloatingButtonView(
            context = this,
            onAnalyze = { runAnalysis(manual = true) },
            onToggleAuto = { enabled -> toggleAuto(enabled) }
        )
        windowManager.addView(
            bubble,
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 40
                y = 220
            }
        )
        // make it draggable
        bubble?.setOnTouchListener(DragTouchListener())
    }

    private fun startCapture() {
        val data = captureResultData ?: return
        val cap = ScreenCapture(this)
        cap.start(captureResultCode, data) { error ->
            main.post {
                bubble?.mode = FloatingButtonView.Mode.ANALYZE
                Log.e(TAG, error)
            }
        }
        capture = cap
    }

    /**
     * Runs one analysis cycle on a background thread, then posts the result to
     * the overlay. Heavy work never happens on the main thread, so the bubble
     * stays responsive even on a slow phone.
     */
    private fun runAnalysis(manual: Boolean) {
        if (analyzing) return
        val cap = capture ?: return
        analyzing = true
        main.post { bubble?.mode = FloatingButtonView.Mode.BUSY }
        scope.launch {
            try {
                val calibration = calibrationStore.load()
                val frame = cap.capture()
                frame?.let { com.example.blockblastplayer.vision.LastFrame.set(it) }
                val reading = frame?.let { BoardReader.read(it, calibration) }
                val frameWidth = frame?.width ?: 0
                val result = if (reading != null && reading.ok && reading.pieces.isNotEmpty()) {
                    BlockBlastSolver().solve(reading.board, reading.pieces)
                } else null
                main.post {
                    if (result != null && result.moves.isNotEmpty() && frameWidth > 0) {
                        // The captured frame and the overlay both use screen
                        // pixels, so normally the scale is exactly 1. We keep
                        // the ratio anyway in case the compositor hands us a
                        // frame at a different resolution.
                        val scale = resources.displayMetrics.widthPixels.toFloat() / frameWidth
                        val src = reading!!.panel
                        val panel = com.example.blockblastplayer.vision.Rect(
                            (src.left * scale).toInt(),
                            (src.top * scale).toInt(),
                            (src.right * scale).toInt(),
                            (src.bottom * scale).toInt()
                        )
                        ghostView?.show(GhostOverlayView.Suggestion(panel, result.moves))
                    }
                    bubble?.mode = if (autoJob?.isActive == true) {
                        FloatingButtonView.Mode.AUTO_ON
                    } else {
                        FloatingButtonView.Mode.ANALYZE
                    }
                    analyzing = false
                }
            } catch (t: Throwable) {
                Log.e(TAG, "analysis failed", t)
                main.post {
                    bubble?.mode = FloatingButtonView.Mode.ANALYZE
                    analyzing = false
                }
            }
        }
    }

    private fun toggleAuto(enabled: Boolean) {
        autoJob?.cancel()
        if (!enabled) {
            autoJob = null
            return
        }
        autoJob = scope.launch {
            while (isActive) {
                runAnalysis(manual = false)
                delay(1200)
            }
        }
    }

    private fun stopEverything() {
        autoJob?.cancel()
        autoJob = null
        scope.cancel()
        capture?.stop()
        capture = null
        ghostView?.let { runCatching { windowManager.removeView(it) } }
        ghostView = null
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    /** Lets the user drag the bubble anywhere without stealing game taps. */
    private inner class DragTouchListener : View.OnTouchListener {
        private var startX = 0f
        private var startY = 0f
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = v.x; startY = v.y
                    touchX = event.rawX; touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    v.x = startX + dx
                    v.y = startY + dy
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    // let the button handle its own click
                    v.performClick()
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1002
        const val ACTION_START = "com.example.blockblastplayer.action.START"
        const val ACTION_STOP = "com.example.blockblastplayer.action.STOP"
        const val ACTION_ANALYZE = "com.example.blockblastplayer.action.ANALYZE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "solver_overlay"
        private const val CHANNEL_NAME = "Solver overlay"

        /** Builds the intent that starts the service with a capture grant. */
        fun startIntent(context: android.content.Context, resultCode: Int, data: Intent): Intent =
            Intent(context, OverlayService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
    }
}