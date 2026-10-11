package com.example.blockblastplayer.solver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Board basics: set/get, parsing, and the bit mask staying in sync. */
class BoardTest {

    @Test
    fun `new board is empty`() {
        val board = Board()
        assertEquals(0, board.filledCount())
        assertEquals(64, board.emptyCount())
        assertEquals(0L, board.mask())
    }

    @Test
    fun `set and read back individual cells`() {
        val board = Board()
        board.set(3, 5, true)
        assertTrue(board.isFilled(3, 5))
        assertFalse(board.isFilled(5, 3))
        assertEquals(1, board.filledCount())
        assertEquals(1L shl (3 * 8 + 5), board.mask())
    }

    @Test
    fun `clearing a cell updates count and mask`() {
        val board = Board()
        board.set(0, 0, true)
        board.set(0, 0, false)
        assertEquals(0, board.filledCount())
        assertEquals(0L, board.mask())
    }

    @Test
    fun `parse reads hash and one as filled`() {
        val board = Board.parse(
            "########",
            "#.......",
            "........",
            "........",
            "........",
            "........",
            "........",
            "..1....."
        )
        assertEquals(9, board.filledCount())
        assertTrue(board.isFilled(0, 7))
        assertTrue(board.isFilled(7, 2))
        assertFalse(board.isFilled(1, 1))
    }

    @Test
    fun `parse rejects a malformed board`() {
        val bad = try { Board.parse("###"); null } catch (e: IllegalArgumentException) { e }
        assertTrue("expected IllegalArgumentException", bad != null)
    }

    @Test
    fun `copy is independent of the original`() {
        val board = Board.parse("........", "........", "###.....", "........", "........", "........", "........", "........")
        val copy = board.copy()
        copy.set(0, 0, true)
        assertFalse(board.isFilled(0, 0))
        assertTrue(copy.isFilled(0, 0))
    }

    @Test
    fun `board reports its size as eight`() {
        assertEquals(8, Board.DEFAULT_SIZE)
        assertEquals(8, Board().size)
    }
}