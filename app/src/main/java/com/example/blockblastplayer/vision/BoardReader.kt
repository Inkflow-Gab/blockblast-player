package com.example.blockblastplayer.vision

import com.example.blockblastplayer.solver.Board
import com.example.blockblastplayer.solver.Piece

/**
 * Finds the 8x8 board panel in a screenshot.
 *
 * The single idea: the panel is a square region whose cell *gaps* are all the
 * same dark navy, and those gaps form one 4-connected network. Filled blocks
 * sit in the wells between the gaps but never break the network, so the
 * bounding box of the largest connected run of navy pixels is the panel -
 * whether the board is empty, half full, or nearly finished.
 *
 * Because the search only follows gap pixels, dark things that are *not* part
 * of the grid (the panel's own drop shadow, a "55 FPS" booster badge floating
 * over the board) end up in separate components and are discarded.
 */
object GridDetector {

    /** Finds the panel, or returns null if the image does not look like the game. */
    fun detectPanel(image: RgbImage, calibration: Calibration = Calibration()): Rect? {
        val w = image.width
        val h = image.height
        val gap = calibration.gapColor.toInt()
        val tol = calibration.gapTolerance
        // 1. Classify every pixel in ONE pass into a single ByteArray:
        //       0 = not gap, 1 = gap and not yet seen, 2 = already seen.
        //    Using one array instead of two booleans halves the peak memory,
        //    which matters because this runs on a low-end phone over a
        //    1.1 megapixel frame.
        val total = w * h
        val state = ByteArray(total)
        var gapPixels = 0
        for (i in 0 until total) {
            if (Rgb.distance(image.pixels[i], gap) <= tol) {
                state[i] = 1
                gapPixels++
            }
        }
        // A real screenshot has a big navy grid; anything tiny is not the game.
        if (gapPixels < total / 200) return null

        // 2. Largest 4-connected component. The stack can never hold more than
        //    one entry per gap pixel, so sizing it by gapPixels keeps it small.
        //    Explicit stack, never recursion - a 1M pixel image would blow up.
        val stack = IntArray(gapPixels + 1)
        var bestCount = 0
        var best: Rect? = null

        for (seed in 0 until total) {
            if (state[seed].toInt() != 1) continue
            var size = 0
            stack[size++] = seed
            state[seed] = 2
            var count = 0
            var minX = w; var maxX = -1; var minY = h; var maxY = -1

            while (size > 0) {
                val index = stack[--size]
                count++
                val y = index / w
                val x = index - y * w
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                if (x > 0) {
                    val n = index - 1
                    if (state[n].toInt() == 1) { state[n] = 2; stack[size++] = n }
                }
                if (x < w - 1) {
                    val n = index + 1
                    if (state[n].toInt() == 1) { state[n] = 2; stack[size++] = n }
                }
                if (y > 0) {
                    val n = index - w
                    if (state[n].toInt() == 1) { state[n] = 2; stack[size++] = n }
                }
                if (y < h - 1) {
                    val n = index + w
                    if (state[n].toInt() == 1) { state[n] = 2; stack[size++] = n }
                }
            }

            if (count > bestCount) {
                bestCount = count
                best = Rect(minX, minY, maxX, maxY)
            }
        }
        return best
    }

    /**
     * Reads the 8x8 occupancy of the board out of [image].
     *
     * Each cell is sampled at its centre. To survive JPEG noise and the odd
     * stray pixel we take the **median** of five samples in a small
     * plus-shape around the centre rather than a single pixel.
     */
    fun readBoard(image: RgbImage, panel: Rect, calibration: Calibration): Board {
        val board = Board()
        val cellW = panel.width.toFloat() / Board.DEFAULT_SIZE
        val cellH = panel.height.toFloat() / Board.DEFAULT_SIZE
        val empty = calibration.emptyColor.toInt()
        val threshold = calibration.filledThreshold
        val radius = maxOf(1, (minOf(cellW, cellH) * 0.12f).toInt())
        val dx = intArrayOf(0, -radius, radius, 0, 0)
        val dy = intArrayOf(0, 0, 0, -radius, radius)

        for (r in 0 until Board.DEFAULT_SIZE) {
            for (c in 0 until Board.DEFAULT_SIZE) {
                val cx = panel.left + ((c + 0.5f) * cellW).toInt()
                val cy = panel.top + ((r + 0.5f) * cellH).toInt()
                val distances = IntArray(5) { i ->
                    Rgb.distance(image.at(cx + dx[i], cy + dy[i]), empty)
                }
                distances.sort()
                board.set(r, c, distances[2] >= threshold)
            }
        }
        return board
    }
}

/** Convenience wrapper so callers do not have to remember the order of steps. */
object BoardReader {

    /** Everything the pipeline managed to read from one screenshot. */
    data class Reading(
        val board: Board,
        val pieces: List<Piece>,
        val panel: Rect,
        val ok: Boolean,
        val message: String = ""
    )

    /**
     * Detects the panel (unless the user pinned one), reads the board, then
     * reads the three tray pieces.
     */
    fun read(image: RgbImage, calibration: Calibration = Calibration()): Reading {
        val panel = calibration.panel ?: GridDetector.detectPanel(image, calibration)
            ?: return Reading(
                board = Board(), pieces = emptyList(), panel = Rect.ZERO,
                ok = false,
                message = "Could not find the 8x8 board. Try Calibrate and pin the grid by hand."
            )

        val board = GridDetector.readBoard(image, panel, calibration)
        val pieces = TrayDetector.detect(image, panel, calibration)

        val message = when {
            pieces.size == 3 -> ""
            pieces.isEmpty() -> "Board found, but no tray pieces were readable."
            else -> "Board found, but only ${pieces.size} of 3 tray pieces were readable."
        }
        return Reading(board, pieces, panel, ok = true, message = message)
    }
}