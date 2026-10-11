package com.example.blockblastplayer.solver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules tests: which placements are legal, and what happens to lines.
 *
 * These are the tests that decide whether the solver models the real game, so
 * each one is written as a concrete board you can draw and check by eye.
 */
class LineClearTest {

    private fun rows(vararg cells: BooleanArray): Board {
        val b = Board()
        cells.forEachIndexed { r, row -> row.forEachIndexed { c, v -> b.set(r, c, v) } }
        return b
    }

    @Test
    fun `a full row is cleared`() {
        // row 7 filled except the last square
        val board = Board()
        for (c in 0 until 7) board.set(7, c, true)
        board.set(0, 0, true) // unrelated block
        assertEquals(0, board.completeRows().size)
        board.set(7, 7, true)
        assertEquals(listOf(7), board.completeRows())

        val cleared = board.clearCompletedLines()
        assertEquals(1, cleared)
        assertEquals(1, board.filledCount()) // only the (0,0) block survives
    }

    @Test
    fun `a full column is cleared`() {
        val board = Board()
        for (r in 0 until 7) board.set(r, 3, true)
        board.set(7, 3, true)
        assertEquals(listOf(3), board.completeColumns())
        board.clearCompletedLines()
        assertEquals(0, board.filledCount())
    }

    @Test
    fun `a row and a column cleared together count as two lines`() {
        val board = Board()
        for (c in 0 until 8) board.set(0, c, true)     // row 0
        for (r in 0 until 8) board.set(r, 0, true)     // column 0
        assertEquals(listOf(0), board.completeRows())
        assertEquals(listOf(0), board.completeColumns())
        assertEquals(2, board.clearCompletedLines())
        assertEquals(0, board.filledCount())
    }

    @Test
    fun `a nearly full board clears nothing`() {
        val board = Board()
        for (c in 0 until 7) board.set(4, c, true)
        assertEquals(0, board.clearCompletedLines())
        assertEquals(7, board.filledCount())
    }

    @Test
    fun `the boolean view and the mask stay in sync after a clear`() {
        val board = Board()
        for (c in 0 until 8) board.set(5, c, true)
        board.clearCompletedLines()
        assertEquals(0L, board.mask())
        for (r in 0 until 8) for (c in 0 until 8) assertFalse(board.isFilled(r, c))
    }

    @Test
    fun `clearCompletedLines is idempotent`() {
        val board = Board()
        for (c in 0 until 8) board.set(2, c, true)
        assertEquals(1, board.clearCompletedLines())
        assertEquals(0, board.clearCompletedLines())
    }
}

/** Placement validity: a piece only goes where all of its blocks land free. */
class PlacementTest {

    private val domino = Piece.of("D", "##")

    @Test
    fun `an empty board accepts a placement anywhere in range`() {
        val mask = Board.empty().mask()
        assertTrue(BoardAnalysis.fits(mask, domino, 0, 0))
        assertTrue(BoardAnalysis.fits(mask, domino, 7, 6))
        assertTrue(BoardAnalysis.fits(mask, domino, 3, 4))
    }

    @Test
    fun `a placement that hangs off the edge is rejected`() {
        val mask = Board.empty().mask()
        assertFalse(BoardAnalysis.fits(mask, domino, 0, 7))
        assertFalse(BoardAnalysis.fits(mask, domino, 8, 0))
        assertFalse(BoardAnalysis.fits(mask, domino, -1, 0))
    }

    @Test
    fun `a placement overlapping a block is rejected`() {
        val board = Board.parse("........", "........", "..##....", "........", "........", "........", "........", "........")
        val mask = board.mask()
        assertFalse(BoardAnalysis.fits(mask, domino, 1, 2))  // row 1 free -> should fit? row1 col2,3 free
        assertFalse(BoardAnalysis.fits(mask, domino, 2, 2))  // row 2 is the blocked row
        assertTrue(BoardAnalysis.fits(mask, domino, 1, 2))
    }

    @Test
    fun `rotations give the expected shapes`() {
        val bar = Piece.of("Bar4", "####")
        val rots = bar.rotations()
        assertEquals(2, rots.size)
        assertEquals(4, rots[0].width)
        assertEquals(4, rots[1].height)
        assertEquals(1, rots[1].width)
    }

    @Test
    fun `a square piece has only one distinct rotation`() {
        assertEquals(1, PieceLibrary.SQUARE_2.rotations().size)
    }

    @Test
    fun `pieces are trimmed to their bounding box`() {
        val p = Piece.of("T", "..#..", "#####", "..#..")
        assertEquals(3, p.height)
        assertEquals(5, p.width)
        assertFalse(p.isFilled(0, 0))
        assertTrue(p.isFilled(0, 2))
    }

    @Test
    fun `place then clear matches playing the move`() {
        val board = Board.parse("........", "........", "........", "........", "........", "........", ".......#", "#######.")
        val mask = board.mask()
        val bar5 = PieceLibrary.PENT_I_H
        assertTrue(BoardAnalysis.fits(mask, bar5, 7, 3))
        val withPiece = BoardAnalysis.place(mask, bar5, 7, 3)
        val (after, lines) = BoardAnalysis.clearLines(withPiece)
        assertEquals(1, lines)
        // Row 7 is now completely empty; only the lone block at (6,7) is left.
        assertEquals(1, java.lang.Long.bitCount(after))
        assertTrue(BoardAnalysis.fits(after, PieceLibrary.MONOMINO, 7, 0))
    }
}