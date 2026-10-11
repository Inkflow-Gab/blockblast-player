package com.example.blockblastplayer.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/**
 * The little draggable bubble: tap it to analyse, tap again to toggle
 * auto-refresh.
 *
 * The service installs a touch listener to drag it. That listener only reacts
 * to a *single* finger, and [onTouchEvent] bails out as soon as a second
 * finger appears - so holding the bubble to drag it never swallows the taps
 * you need to actually play the suggested move.
 */
@SuppressLint("ViewConstructor")
class FloatingButtonView(
    context: Context,
    private val onAnalyze: () -> Unit,
    private val onToggleAuto: (Boolean) -> Unit
) : View(context) {

    enum class Mode { ANALYZE, AUTO_ON, AUTO_OFF, BUSY }

    var mode: Mode = Mode.ANALYZE
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val radius = 34f * density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.WHITE
    }

    /** Set while a second finger is down, so the release is not a click. */
    private var multiTouch = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (radius * 2.6f).roundToInt()
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val bg: Int
        val label: String
        when (mode) {
            Mode.ANALYZE -> { bg = Color.parseColor("#1565C0"); label = "GO" }
            Mode.AUTO_ON -> { bg = Color.parseColor("#2E7D32"); label = "ON" }
            Mode.AUTO_OFF -> { bg = Color.parseColor("#455A64"); label = "OFF" }
            Mode.BUSY -> { bg = Color.parseColor("#EF6C00"); label = "..." }
        }
        bgPaint.color = bg
        canvas.drawCircle(cx, cy, radius, bgPaint)
        canvas.drawCircle(cx, cy, radius, strokePaint)

        textPaint.textSize = radius * 0.95f
        canvas.drawText(label, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                multiTouch = false
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // a second finger means "I am also playing right now"
                multiTouch = true
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (multiTouch) return true
                when (mode) {
                    Mode.AUTO_ON, Mode.AUTO_OFF -> {
                        val turningOn = mode == Mode.AUTO_OFF
                        mode = if (turningOn) Mode.AUTO_ON else Mode.AUTO_OFF
                        onToggleAuto(turningOn)
                    }
                    Mode.ANALYZE -> onAnalyze()
                    Mode.BUSY -> Unit
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}