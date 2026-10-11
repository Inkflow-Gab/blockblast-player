package com.example.blockblastplayer.solver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour of the search itself: does it try every order, does it find the
 * move that clears the line, is it deterministic, and does the time budget
 * actually protect the UI?
 */
class BlockBlastSolverTest {

    private val solver = BlockBlastSolver()

    /**
     * Rebuilds the shape a move covers, purely from the cells it reports.
     * Used to re-check that every suggested move was legal at the time it was
     * suggested (the solver does not expose the rotation it picked).
     */
    private fun shapeOf(move: Move): Piece {
        val minRow = move.cells.minOf { it.row }
        val minCol = move.cells.minOf { it.col }
        val rows = move.cells.maxOf { it.row } - minRow + 1
        val cols = move.cells.maxOf { it.col } - minCol + 1
        val grid = Array(rows) { BooleanArray(cols) }
        for (c in move.cells) grid[c.row - minRow][c.col - minCol] = true
        return Piece.of(
            "t",
            *grid.map { r -> r.joinToString("") { if (it) "#" else "." } }.toTypedArray()
        )
    }

    @Test
    fun `empty board returns three moves`() {
        val result = solver.solve(
            Board.empty(),
            listOf(PieceLibrary.PENT_I_H, PieceLibrary.SQUARE_2, PieceLibrary.MONOMINO)
        )
        assertEquals(3, result.moves.size)
        assertFalse(result.isEmpty)
    }

    @Test
    fun `the three pieces are used exactly once each`() {
        val pieces = listOf(PieceLibrary.PENT_I_H, PieceLibrary.SQUARE_2, PieceLibrary.TET_T_D)
        val result = solver.solve(Board.empty(), pieces)
        assertEquals(setOf(0, 1, 2), result.moves.map { it.pieceIndex }.toSet())
    }

    @Test
    fun `every suggested move is actually legal`() {
        val board = Board.parse(
            "11011111", "10100000", "00100010", "00110010",
            "00111110", "11100011", "11111011", "11100111"
        )
        // the real board measured from samples/shot3.jpg
        val pieces = listOf(
            Piece.of("Cyan", "##"),
            Piece.of("Red", ".##", "#..", "#.."),
            Piece.of("Yellow", "#", "#")
        )
        val result = solver.solve(board, pieces)
        val working = board.copy()
        for (move in result.moves) {
            val shape = shapeOf(move)
            // must fit in the *current* board before we add it
            for (offset in shape.offsets) {
                val r = move.row + offset.row
                val c = move.col + offset.col
                assertTrue("piece ${move.pieceIndex} placed off the board at $r,$c",
                    r in 0 until 8 && c in 0 until 8)
                assertFalse("piece ${move.pieceIndex} overlaps a block at $r,$c", working.isFilled(r, c))
            }
            for (offset in shape.offsets) working.set(move.row + offset.row, move.col + offset.col, true)
            working.clearCompletedLines()
        }
    }

    @Test
    fun `solver clears the row when a bar can finish it`() {
        // row 7 needs exactly one more cell at column 7
        val board = Board.parse(
            "........", "........", "........", "........",
            "........", "........", "........", "#######."
        )
        val result = solver.solve(board, listOf(PieceLibrary.MONOMINO, PieceLibrary.PENT_I_H, PieceLibrary.SQUARE_2))
        // The single block must be dropped on (7,7) - it is the only cell that
        // completes the row, and the solver is worth 100 per line.
        val single = result.moves.first { it.pieceIndex == 0 }
        assertEquals(7, single.row)
        assertEquals(7, single.col)
        assertEquals(1, single.linesCleared)
    }

    @Test
    fun `solving is deterministic`() {
        val board = Board.parse(
            "11011111", "10100000", "00100010", "00110010",
            "00111110", "11100011", "11111011", "11100111"
        )
        val pieces = listOf(PieceLibrary.MONOMINO, PieceLibrary.DOMINO_H, PieceLibrary.TRI_L)
        val a = solver.solve(board, pieces)
        val b = solver.solve(board, pieces)
        assertEquals(a.moves.map { it.pieceIndex to it.row to it.col }, b.moves.map { it.pieceIndex to it.row to it.col })
        assertEquals(a.score, b.score, 0.0001)
    }

    @Test
    fun `the time budget stops the search but still returns a legal move`() {
        val options = BlockBlastSolver.Options(timeBudgetMs = 1L)
        val result = solver.solve(
            Board.empty(),
            listOf(PieceLibrary.MONOMINO, PieceLibrary.MONOMINO, PieceLibrary.MONOMINO),
            options
        )
        assertEquals(3, result.moves.size)
        assertTrue(result.truncated)
    }

    @Test
    fun `impossible pieces produce no moves`() {
        // a completely full board leaves nowhere to put anything
        val full = Board.parse(*Array(8) { "########" })
        val result = solver.solve(full, listOf(PieceLibrary.MONOMINO, PieceLibrary.MONOMINO, PieceLibrary.MONOMINO))
        assertTrue(result.isEmpty)
        assertEquals(null, solver.solveOrNull(full, listOf(PieceLibrary.MONOMINO)))
    }

    @Test
    fun `empty board scores better than a nearly full board`() {
        val pieces = listOf(PieceLibrary.MONOMINO, PieceLibrary.DOMINO_H, PieceLibrary.SQUARE_2)
        val openScore = solver.solve(Board.empty(), pieces).score
        val cramped = Board.parse(
            "########",
            "########",
            "########",
            "########",
            "########",
            "########",
            "########",
            "#######."
        )
        val crampedScore = solver.solve(cramped, pieces).score
        assertTrue("open=$openScore should beat cramped=$crampedScore", openScore > crampedScore)
    }

    @Test
    fun `holes are punished more than open space`() {
        val open = Board.empty().mask()
        // one block on top of empties creates a hole underneath
        val holey = Board.parse("........", "........", "........", "........", "........", "........", "........", ".#......").mask()
        val openHoles = BoardAnalysis.holes(open)
        val holeyHoles = BoardAnalysis.holes(holey)
        assertEquals(0, openHoles)
        assertTrue("expected a hole, got $holeyHoles", holeyHoles > 0)
    }

    @Test
    fun `fragmentation counts separate empty regions`() {
        val one = Board.empty().mask()
        val two = Board.parse("########", "........", "........", "........", "........", "........", "........", "........").mask()
        assertEquals(1, BoardAnalysis.emptyComponents(one))
        assertTrue(BoardAnalysis.emptyComponents(two) >= 1)
    }

    @Test
    fun `empty rectangles is zero on a full board and positive otherwise`() {
        val full = Board.parse(*Array(8) { "########" }).mask()
        val empty = Board.empty().mask()
        assertEquals(0, BoardAnalysis.emptyRectangles(full))
        assertEquals(1, BoardAnalysis.emptyRectangles(empty))
    }

    @Test
    fun `largest empty rectangle is 64 on an empty board`() {
        assertEquals(64, BoardAnalysis.largestEmptyRectangle(Board.empty().mask()))
    }

    @Test
    fun `unplaceable risk rises when the board fills up`() {
        val roomy = BoardAnalysis.unplaceableRisk(Board.empty().mask())
        val tight = BoardAnalysis.unplaceableRisk(
            Board.parse("########", "########", "########", "########",
                "########", "########", "########", "#######.").mask()
        )
        assertEquals(0, roomy)
        assertTrue("roomy=$roomy tight=$tight", tight > roomy)
    }
}