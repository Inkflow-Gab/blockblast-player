package com.example.blockblastplayer.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.example.blockblastplayer.solver.Board
import com.example.blockblastplayer.solver.Move
import com.example.blockblastplayer.vision.Rect

/**
 * The full-screen transparent view that draws the suggestion on top of the
 * game.
 *
 * It shows, for each of the three moves, a translucent "ghost" square over
 * every cell the piece would cover, a coloured border, and a big badge with
 * the step number (1, 2, 3) so it is obvious **what to play and in which
 * order**. Cells that would be cleared by that move get a pulsing outline.
 *
 * The view itself is clickable = false; it exists purely to draw. All tapping
 * is the user's - this app never injects input.
 */
class GhostOverlayView(context: Context) : View(context) {

    /** What to draw right now. */
    data class Suggestion(
        val panel: Rect,
        val moves: List<Move>,
        val showCellNumbers: Boolean = true,
        val highlightLines: Boolean = true
    )

    private var suggestion: Suggestion? = null

    // Colours chosen to stay visible over the game's dark navy board.
    private val palette = intArrayOf(
        Color.parseColor("#4FC3F7"),  // step 1 - light blue
        Color.parseColor("#FFB74D"),  // step 2 - amber
        Color.parseColor("#81C784")   // step 3 - green
    )

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val badgeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val rect = RectF()

    /** Alpha of the ghost fill, 0..255. */
    var ghostAlpha: Int = 110
        set(value) { field = value.coerceIn(0, 255); invalidate() }

    fun show(suggestion: Suggestion?) {
        this.suggestion = suggestion
        invalidate()
    }

    fun clear() {
        suggestion = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val s = suggestion ?: return
        if (s.moves.isEmpty()) return
        val panel = s.panel
        if (panel.width <= 0 || panel.height <= 0) return

        val cellW = panel.width.toFloat() / Board.DEFAULT_SIZE
        val cellH = panel.height.toFloat() / Board.DEFAULT_SIZE
        val inset = minOf(cellW, cellH) * 0.10f

        for ((index, move) in s.moves.withIndex()) {
            val colour = palette[index % palette.size]
            fillPaint.color = colour
            fillPaint.alpha = ghostAlpha
            strokePaint.color = colour
            strokePaint.alpha = 255

            for (cell in move.cells) {
                val left = panel.left + cell.col * cellW
                val top = panel.top + cell.row * cellH
                rect.set(left + inset, top + inset, left + cellW - inset, top + cellH - inset)
                canvas.drawRoundRect(rect, inset, inset, fillPaint)
                canvas.drawRoundRect(rect, inset, inset, strokePaint)
            }

            // A move that clears lines deserves a stronger, dashed outline.
            if (s.highlightLines && move.linesCleared > 0) {
                strokePaint.strokeWidth = 6f
                strokePaint.alpha = 255
                for (cell in move.cells) {
                    val left = panel.left + cell.col * cellW
                    val top = panel.top + cell.row * cellH
                    rect.set(left + inset, top + inset, left + cellW - inset, top + cellH - inset)
                    canvas.drawRoundRect(rect, inset, inset, strokePaint)
                }
                strokePaint.strokeWidth = 3f
            }

            // The numbered badge, parked on the top-left block of the piece.
            val anchor = move.badgeAnchor()
            val bx = panel.left + anchor.col * cellW + cellW * 0.5f
            val by = panel.top + anchor.row * cellH + cellH * 0.5f
            val radius = cellW * 0.30f
            badgeFill.color = colour
            canvas.drawCircle(bx, by, radius, badgeFill)
            badgeText.textSize = radius * 1.35f
            val textY = by - (badgeText.ascent() + badgeText.descent()) / 2f
            canvas.drawText((index + 1).toString(), bx, textY, badgeText)
        }
    }
}