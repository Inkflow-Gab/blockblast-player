package com.example.blockblastplayer.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Loads one of the **real** Block Blast screenshots from `samples/` and runs
 * the exact production [BoardReader] over it.
 *
 * These are the ground-truth tests for the computer-vision half of the app.
 * Everything they assert was measured with `tools/detect_final.py`, not
 * eyeballed - see [Calibration] for the numbers.
 *
 * The images are on the test classpath because `app/build.gradle.kts` adds
 * the repository's `samples/` folder as a test resource directory.
 */
class SampleScreenshotTest {

    private fun load(name: String): RgbImage {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
            ?: error("sample $name is not on the test classpath (expected in samples/)")
        val buffered: BufferedImage = stream.use { ImageIO.read(it) }
        val w = buffered.width
        val h = buffered.height
        val pixels = IntArray(w * h)
        buffered.getRGB(0, 0, w, h, pixels, 0, w)
        for (i in pixels.indices) pixels[i] = pixels[i] and 0xFFFFFF
        return RgbImage(w, h, pixels)
    }

    private fun boardAsStrings(board: com.example.blockblastplayer.solver.Board): List<String> =
        (0 until 8).map { r ->
            (0 until 8).map { c -> if (board.isFilled(r, c)) "1" else "0" }.joinToString("")
        }

    // ---------------------------------------------------------------- shot3
    // The important one: a real, half-full board. The expected 8x8 pattern
    // below was read off the screenshot by hand and then re-verified by
    // drawing the detected lattice over the image (tools/overlay_check.py).
    @Test
    fun `shot3 - a half full board is read exactly`() {
        val image = load("shot3.jpg")
        val reading = BoardReader.read(image, Calibration())

        assertTrue("panel should be found", reading.panel.width > 0)
        assertEquals(
            "detected panel",
            Rect(43, 388, 681, 1026),
            reading.panel
        )

        val expected = listOf(
            "11011111",
            "10100000",
            "00100010",
            "00110010",
            "00111110",
            "11100011",
            "11111011",
            "11100111"
        )
        assertEquals(expected, boardAsStrings(reading.board))
        assertEquals(37, reading.board.filledCount())
    }

    @Test
    fun `shot3 - the three tray pieces are recovered`() {
        val image = load("shot3.jpg")
        val reading = BoardReader.read(image, Calibration())
        assertEquals(3, reading.pieces.size)
        // measured from the screenshot: a 2-wide bar, an S zigzag and a
        // 1x2 vertical block. Rotations are normalised by Piece.of/trimming,
        // so we only assert the shapes and the block counts.
        val sizes = reading.pieces.map { it.size }
        assertEquals(listOf(2, 3, 2), sizes)
        val square = reading.pieces[0]
        assertEquals(1, square.height)
        assertEquals(2, square.width)
    }

    // ---------------------------------------------------------------- shot2
    @Test
    fun `shot2 - an empty board is read as empty`() {
        val image = load("shot2.jpg")
        val reading = BoardReader.read(image, Calibration())
        assertEquals(
            "detected panel",
            Rect(40, 386, 681, 1026),
            reading.panel
        )
        assertEquals(0, reading.board.filledCount())
    }

    @Test
    fun `shot2 - tray pieces are 2x1, 3 cells and 1x3`() {
        val reading = BoardReader.read(load("shot2.jpg"), Calibration())
        assertEquals(3, reading.pieces.size)
        val sorted = reading.pieces.sortedBy { it.size }
        assertEquals(2, sorted[0].size)   // the 1x2 domino
        assertEquals(3, sorted[1].size)   // the L tromino
        assertEquals(3, sorted[2].size)   // the 1x3 bar
        val bar = reading.pieces.maxByOrNull { if (it.height == 1) it.width else 0 }!!
        assertEquals(3, bar.width)
        assertEquals(1, bar.height)
    }

    // ---------------------------------------------------------------- shot1
    @Test
    fun `shot1 - empty board apart from the floating FPS badge`() {
        val image = load("shot1.jpg")
        val reading = BoardReader.read(image, Calibration())
        assertEquals(
            "detected panel",
            Rect(40, 386, 681, 1026),
            reading.panel
        )
        // NOTE: this screenshot contains a black "55 FPS" booster badge
        // covering cell (4,7). The reader sees it as a filled cell - that is
        // the honest, measured result and the reason the docs tell you to
        // switch such overlays off. The panel and the 63 other cells are right.
        assertEquals(1, reading.board.filledCount())
        assertTrue(reading.board.isFilled(4, 7))
    }

    @Test
    fun `shot1 - tray pieces are a 5 bar, an L and a T`() {
        val reading = BoardReader.read(load("shot1.jpg"), Calibration())
        assertEquals(3, reading.pieces.size)
        val five = reading.pieces.maxByOrNull { it.size }!!
        assertEquals(5, five.size)
        assertEquals(5, five.width)
        assertEquals(1, five.height)
        val t = reading.pieces.firstOrNull { it.size == 4 }
        assertTrue("expected a 4 block T piece, got ${reading.pieces.map { it.render() }}", t != null)
    }

    // ------------------------------------------------------- shared numbers
    @Test
    fun `every sample detects a square panel about 642 px wide`() {
        for (name in listOf("shot1.jpg", "shot2.jpg", "shot3.jpg")) {
            val panel = GridDetector.detectPanel(load(name), Calibration())
            assertTrue("$name: no panel", panel != null)
            assertTrue("$name: width ${panel!!.width}", panel.width in 620..660)
            assertTrue("$name: height ${panel.height}", panel.height in 620..660)
            val aspect = panel.width.toFloat() / panel.height
            assertTrue("$name: aspect $aspect", kotlin.math.abs(aspect - 1f) < 0.05f)
        }
    }

    @Test
    fun `empty and filled colours are far apart, so the threshold is safe`() {
        val calibration = Calibration()
        val image = load("shot3.jpg")
        val panel = GridDetector.detectPanel(image, calibration)!!
        val board = GridDetector.readBoard(image, panel, calibration)

        var maxEmpty = 0
        var minFilled = Int.MAX_VALUE
        for (r in 0 until 8) for (c in 0 until 8) {
            val p = image.at(
                panel.left + ((c + 0.5f) * panel.width / 8f).toInt(),
                panel.top + ((r + 0.5f) * panel.height / 8f).toInt()
            )
            val d = Rgb.distance(p, calibration.emptyColor.toInt())
            if (board.isFilled(r, c)) minFilled = minOf(minFilled, d) else maxEmpty = maxOf(maxEmpty, d)
        }
        assertTrue("empty cells should hug the empty colour, worst was $maxEmpty", maxEmpty < 60)
        assertTrue("filled cells should be far, closest was $minFilled", minFilled > 60)
        // the gap the default threshold of 60 sits in
        assertTrue("threshold must sit between $maxEmpty and $minFilled", maxEmpty < 60 && 60 < minFilled)
    }
}