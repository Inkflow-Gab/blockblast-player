package com.example.blockblastplayer.solver

/**
 * One suggested placement: put [pieceIndex] (the index into the round's piece
 * list) with its top-left corner on board cell ([row], [col]).
 *
 * [linesCleared] is how many rows/columns disappeared as a result, and
 * [cells] lists every board cell the piece covers - the overlay uses it to
 * draw the ghost blocks.
 */
data class Move(
    val pieceIndex: Int,
    val row: Int,
    val col: Int,
    val linesCleared: Int = 0,
    val cells: List<Cell> = emptyList()
) {
    /** Where to write the "1", "2", "3" badge for this move. */
    fun badgeAnchor(): Cell =
        cells.minByOrNull { it.row } ?: Cell(row, col)
}

data class Cell(val row: Int, val col: Int)

/**
 * The full answer from the solver: the order the three pieces should be
 * played in, plus the score breakdown that made us pick it.
 */
data class SolveResult(
    val moves: List<Move>,
    val score: Double,
    val breakdown: ScoreBreakdown,
    val evaluatedSequences: Long,
    val truncated: Boolean
) {
    val isEmpty: Boolean get() = moves.isEmpty()
}

/** The individual heuristic terms, kept separate so the UI can explain the choice. */
data class ScoreBreakdown(
    val lines: Int,
    val combo: Int,
    val holes: Int,
    val fragments: Int,
    val freeSpace: Int,
    val risk: Int,
    val total: Double
)

/** Raised when the three given pieces cannot all be placed at all. */
class UnsolvableBoardException(message: String) : IllegalStateException(message)