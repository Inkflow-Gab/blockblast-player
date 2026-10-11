package com.example.blockblastplayer.vision

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager

/**
 * Wraps Android's `MediaProjection` so we can grab one frame of whatever is
 * on screen and turn it into an [RgbImage].
 *
 * The app never *sends* anything anywhere: the frame is analysed in memory and
 * thrown away. That is worth saying out loud, because "screen capture" sounds
 * alarming - see the permission explainer in the UI.
 *
 * Android 14 note: `getMediaProjection()` must not be called before a
 * foreground service of type `mediaProjection` is running, so this class is
 * always driven from [com.example.blockblastplayer.overlay.OverlayService].
 */
class ScreenCapture(private val context: Context) {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    val isRunning: Boolean get() = projection != null

    /** Real screen size in pixels - used to keep the overlay aligned. */
    fun screenSize(): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }

    /**
     * Starts mirroring the screen into an off-screen buffer.
     * Must be called from a foreground service on Android 14+.
     */
    fun start(resultCode: Int, resultData: Intent, onError: (String) -> Unit = {}) {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mgr = manager
        val proj = try {
            mgr.getMediaProjection(resultCode, resultData)
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection denied", e)
            onError("Screen capture was not allowed: ${e.message}")
            return
        }
        projection = proj

        val (width, height) = screenSize()
        val density = context.resources.displayMetrics.densityDpi

        handlerThread = HandlerThread("bb-capture").also { it.start() }
        handler = Handler(handlerThread!!.looper)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection stopped by the system")
                stop()
            }
        }, handler)

        virtualDisplay = proj.createVirtualDisplay(
            "BlockBlastPlayer",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, handler
        )
    }

    /**
     * Grabs the next available frame.
     *
     * The MediaProjection buffer always holds the newest frame, so we take the
     * latest one available and drop stale ones - that is what keeps a live
     * auto-refresh responsive.
     */
    @Synchronized
    fun capture(maxWaitMs: Long = 400): RgbImage? {
        val reader = imageReader ?: return null
        var image: Image? = null
        val deadline = System.currentTimeMillis() + maxWaitMs
        // wait for a frame
        while (System.currentTimeMillis() < deadline) {
            image = reader.acquireLatestImage()
            if (image != null) break
            try { Thread.sleep(16) } catch (e: InterruptedException) { return null }
        }
        if (image == null) return null

        return try {
            val plane = image.planes[0]
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val buffer = plane.buffer
            val w = image.width
            val h = image.height
            val pixels = IntArray(w * h)

            // PixelFormat.RGBA_8888 stores the bytes in memory as R,G,B,A.
            // Android's ByteBuffer has no bulk get(IntArray), so we read one
            // row at a time and assemble the pixels by hand. Going row by row
            // also skips any row padding for free.
            val rowBytes = ByteArray(rowStride)
            buffer.rewind()
            var row = 0
            while (row < h && buffer.remaining() >= rowStride) {
                buffer.get(rowBytes)
                var o = 0
                for (x in 0 until w) {
                    if (o + 2 < rowBytes.size) {
                        val r = rowBytes[o].toInt() and 0xFF
                        val g = rowBytes[o + 1].toInt() and 0xFF
                        val b = rowBytes[o + 2].toInt() and 0xFF
                        pixels[row * w + x] = (r shl 16) or (g shl 8) or b
                    }
                    o += pixelStride
                }
                row++
            }
            RgbImage(w, h, pixels)
        } catch (e: Exception) {
            Log.e(TAG, "capture failed", e)
            null
        } finally {
            image.close()
        }
    }

    fun stop() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        handlerThread?.quitSafely()
        virtualDisplay = null
        imageReader = null
        projection = null
        handlerThread = null
        handler = null
    }

    companion object {
        private const val TAG = "ScreenCapture"

        /** The request code used when asking the user for capture permission. */
        const val REQUEST_CODE = 1001

        /** Builds the consent intent for the system capture dialog. */
        fun requestIntent(activity: Activity): Intent {
            val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            return manager.createScreenCaptureIntent()
        }
    }
}

/** Converts an Android [Bitmap] into an [RgbImage] (used by the calibration preview). */
fun Bitmap.toRgbImage(): RgbImage {
    val w = width
    val h = height
    val pixels = IntArray(w * h)
    getPixels(pixels, 0, w, 0, 0, w, h)
    for (i in pixels.indices) pixels[i] = pixels[i] and 0xFFFFFF
    return RgbImage(w, h, pixels)
}