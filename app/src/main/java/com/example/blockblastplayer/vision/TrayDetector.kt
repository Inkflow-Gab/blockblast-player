package com.example.blockblastplayer.vision

import com.example.blockblastplayer.solver.Piece
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Reads the three blocks waiting in the tray under the board.
 *
 * ## Measured facts (`tools/detect_final.py`)
 *
 * ```
 * tray band          y 1177..1247 on a 720x1600 screen
 * tray slot centres  x = 149.5, 359.5, 569.5   (evenly spaced, pitch 210)
 * tray block size    ~35 px      <-- much smaller than the 80 px board cell
 * ```
 *
 * The pieces are found by **colour**, not by position:
 *
 *  1. scan everything below the board for pixels far from both the page
 *     background and the navy gap colour (a piece is purple, orange, green,
 *     cyan, red or yellow - never blue-on-blue),
 *  2. group them into blobs left-to-right, which gives the three tray slots
 *     in tray order,
 *  3. work out the size of one block, then snap the blob onto a grid of that
 *     size to recover the shape.
 *
 * ## The fiddly part: the block size
 *
 * It is not known up front, so we estimate it. Every horizontal run of tray
 * pixels covers a whole number of blocks, so we score each candidate size by
 * how far the runs are from being exact multiples of it, and keep the
 * **largest** candidate with the lowest total error. "Largest" matters: many
 * smaller sizes also divide the runs, but they are just fractions of the
 * truth, and overshooting would claim a block is several pixels wide.
 *
 * The search range is derived from the *board* cell size (a tray block is
 * always smaller than a board cell), which keeps it from drifting.
 *
 * ## The other fiddly part: shadows
 *
 * Block Blast draws a soft shadow under each block, so a measured span can be
 * `n * blockSize + 1` pixels. That is why the grid is sized with
 * `round(span / blockSize)` rather than integer division, and why snapping is
 * done by rounding to the nearest block and then clamping - otherwise every
 * shape would come out one row and one column too big.
 *
 * [Calibration.trayBlockSize] lets the user override the estimate.
 */
object TrayDetector {

    /** Reads the tray. Returns fewer than three pieces if the tray is cut off. */
    fun detect(image: RgbImage, panel: Rect, calibration: Calibration): List<Piece> {
        val bg = calibration.backgroundColor.toInt()
        val gap = calibration.gapColor.toInt()
        val threshold = calibration.trayThreshold

        // Only look below the board - that is where the tray is.
        val yStart = (panel.bottom + 12).coerceIn(0, maxOf(0, image.height - 1))
        val points = ArrayList<Int>()
        for (y in yStart until image.height) {
            val base = y * image.width
            for (x in 0 until image.width) {
                val p = image.pixels[base + x]
                if (Rgb.distance(p, bg) > threshold && Rgb.distance(p, gap) > threshold) {
                    points.add(base + x)
                }
            }
        }
        if (points.isEmpty()) return emptyList()

        // Group the pixels into slots: the three tray slots are far apart, so a
        // gap in x separates them.
        val xs = points.map { it % image.width }.sorted()
        val slots = clusterByGap(xs, minGap = maxOf(1, (image.width * 0.04f).toInt()))
        if (slots.isEmpty()) return emptyList()

        val blockSize = calibration.trayBlockSize.takeIf { it > 0 }
            ?: estimateBlockSize(image, slots, panel)

        return slots.mapNotNull { slot ->
            shapeFrom(image, points.filter { it % image.width in slot }, blockSize)
        }
    }

    /** Splits sorted values into runs separated by more than [minGap]. */
    private fun clusterByGap(sorted: List<Int>, minGap: Int): List<IntRange> {
        if (sorted.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var start = sorted[0]
        var prev = sorted[0]
        for (v in sorted.drop(1)) {
            if (v - prev > minGap) {
                ranges.add(start..prev)
                start = v
            }
            prev = v
        }
        ranges.add(start..prev)
        // a handful of stray pixels is noise, not a piece
        return ranges.filter { it.count() > 3 }
    }

    /**
     * Picks the block size that best explains every horizontal run of tray
     * pixels across all slots.
     */
    private fun estimateBlockSize(image: RgbImage, slots: List<IntRange>, panel: Rect): Int {
        val runs = mutableListOf<Int>()
        for (slot in slots) {
            var runStart = -1
            var lastX = -1
            for (x in slot) {
                if (lastX >= 0 && x != lastX + 1) {
                    runs.add(lastX - runStart + 1)
                    runStart = x
                }
                if (runStart < 0) runStart = x
                lastX = x
            }
            if (runStart >= 0) runs.add(lastX - runStart + 1)
        }
        // ignore runs too short to be a block (antialiasing along an edge)
        val meaningful = runs.filter { it >= 10 }
        if (meaningful.isEmpty()) return DEFAULT_BLOCK

        // A tray block is smaller than a board cell but not tiny. Deriving the
        // range from the measured cell size keeps the search sane.
        val cell = minOf(panel.width, panel.height).toFloat() / 8f
        val low = maxOf(8, (cell * 0.25f).roundToInt())
        val high = maxOf(low + 1, (cell * 0.70f).roundToInt())

        var bestSize = DEFAULT_BLOCK
        var bestCost = Int.MAX_VALUE
        for (size in high downTo low) {
            var cost = 0L
            for (run in meaningful) {
                val rem = run % size
                cost += if (rem == 0) 0 else minOf(rem, size - rem)
                if (cost >= bestCost) break
            }
            // `downTo` means a later candidate is never larger, so keeping the
            // first one that ties favours the larger block size.
            if (cost < bestCost) {
                bestCost = cost.toInt()
                bestSize = size
            }
        }
        return bestSize
    }

    /**
     * Snaps the pixels of one tray slot onto a grid of [blockSize] and turns
     * the result into a [Piece].
     */
    private fun shapeFrom(image: RgbImage, cells: List<Int>, blockSize: Int): Piece? {
        if (cells.isEmpty() || blockSize <= 0) return null
        var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE; var maxY = Int.MIN_VALUE
        for (i in cells) {
            val x = i % image.width
            val y = i / image.width
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        // +1 because maxX/minX are inclusive endpoints; round() absorbs the
        // extra shadow pixel that would otherwise push this up by one.
        val cols = ((maxX - minX + 1).toFloat() / blockSize).roundToInt()
        val rows = ((maxY - minY + 1).toFloat() / blockSize).roundToInt()
        if (rows !in 1..5 || cols !in 1..5) return null

        val grid = Array(rows) { BooleanArray(cols) }
        for (i in cells) {
            val x = i % image.width
            val y = i / image.width
            // Round to the nearest block, then clamp: the shadow pixel on the
            // far edge must land on the last cell, not spill into a new one.
            val col = (((x - minX).toFloat() / blockSize).roundToInt()).coerceIn(0, cols - 1)
            val row = (((y - minY).toFloat() / blockSize).roundToInt()).coerceIn(0, rows - 1)
            grid[row][col] = true
        }
        val art = grid.map { row -> row.joinToString("") { if (it) "#" else "." } }
        if (art.none { it.contains('#') }) return null
        return Piece.of("Tray", *art.toTypedArray())
    }

    /** Fallback when no run information is available; the measured value. */
    private const val DEFAULT_BLOCK = 35
}