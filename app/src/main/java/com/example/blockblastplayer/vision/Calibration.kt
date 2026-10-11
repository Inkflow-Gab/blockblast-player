package com.example.blockblastplayer.vision

/**
 * How to find the board in a screenshot.
 *
 * ## Where these numbers come from
 *
 * Every constant below was *measured* from the three real screenshots in
 * `samples/` with `tools/detect_final.py`, not guessed. Re-run
 *
 * ```
 * python3 tools/detect_final.py samples/shot1.jpg samples/shot2.jpg samples/shot3.jpg
 * ```
 *
 * to print them again. The raw measurements were:
 *
 * ```
 * screen                720 x 1600
 * board panel           x 40..681 (w=642), y 386..1026 (h=641)   aspect 1.0016
 * cell size             80.25 x 80.13 px
 * empty cell centre     RGB(38, 46, 85)
 * grid gap / panel edge RGB(25, 33, 69)
 * page background       RGB(58, 82, 146)
 * tray block size       ~35 px  (much smaller than a board cell)
 * tray slot centres x   149.5, 359.5, 569.5   (pitch 210)
 * ```
 *
 * The two decisions that matter:
 *
 *  * **The panel is found by flood fill, not by a row/column threshold.**
 *    An earlier version counted "dark pixels per row" and worked on an empty
 *    board but fell over as soon as blocks filled the middle, because a row
 *    of solid blocks is not dark. The gaps between the cells are *always*
 *    navy and *always* 4-connected to each other, so the largest connected
 *    component of navy pixels is exactly the panel - empty or full.
 *    It also ignores the panel's drop shadow and the "55 FPS" booster badge,
 *    because those are dark but not connected to the gap network.
 *
 *  * **Empty vs filled is one distance threshold.** Empty cells sit within 14
 *    of `RGB(38,46,85)`; the filled cells measured across all screenshots sit
 *    at least 133 away. A threshold of 60 sits in the middle of that gap with
 *    a huge margin on both sides, so it is not a delicate choice.
 */
data class Calibration(
    /** Board panel rectangle in screenshot pixels, or null to auto-detect. */
    val panel: Rect? = null,
    /** Colour of an empty cell centre. */
    val emptyColor: Rgb = DEFAULT_EMPTY,
    /** A cell counts as filled when it is at least this far from [emptyColor]. */
    val filledThreshold: Int = DEFAULT_FILLED_THRESHOLD,
    /** Colour of the navy gap between cells; used to find the panel. */
    val gapColor: Rgb = DEFAULT_GAP,
    /** How close a pixel must be to [gapColor] to be part of the gap network. */
    val gapTolerance: Int = DEFAULT_GAP_TOLERANCE,
    /** Page background, used to tell tray pieces from the background. */
    val backgroundColor: Rgb = DEFAULT_BACKGROUND,
    /** A tray pixel must be this far from the background to be a piece. */
    val trayThreshold: Int = DEFAULT_TRAY_THRESHOLD,
    /** Block size of a tray piece in pixels; 0 means "estimate it". */
    val trayBlockSize: Int = 0
) {

    /** Copy with a panel that the detector just found, for the "save" button. */
    fun withPanel(rect: Rect): Calibration = copy(panel = rect)

    /** True when the user has pinned the grid by hand. */
    val isManualPanel: Boolean get() = panel != null

    companion object {
        // --- measured values, see the class comment -------------------------
        val DEFAULT_EMPTY = Rgb(38, 46, 85)
        val DEFAULT_GAP = Rgb(25, 33, 69)
        val DEFAULT_BACKGROUND = Rgb(58, 82, 146)

        /**
         * Midpoint of the measured separation gap.
         * empty cells  <= 14 away, filled cells >= 133 away -> 60 is safe.
         */
        const val DEFAULT_FILLED_THRESHOLD = 60

        /** How far a pixel may be from the gap colour and still be "gap". */
        const val DEFAULT_GAP_TOLERANCE = 26

        /** Tray pieces are much further from the background than that. */
        const val DEFAULT_TRAY_THRESHOLD = 150
    }
}

/** An integer rectangle in image/screen pixels. */
data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left + 1
    val height: Int get() = bottom - top + 1
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom

    companion object {
        val ZERO = Rect(0, 0, -1, -1)
    }
}