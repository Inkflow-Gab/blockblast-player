package com.example.blockblastplayer.solver

/**
 * Read-only measurements taken on a board. These are the "facts" the
 * heuristic turns into a score, so they are kept apart from the weighting
 * itself (see [HeuristicWeights]) - that makes the weights easy to explain
 * and tune in the school report.
 *
 * All functions take an already-computed 64-bit mask so callers can analyse a
 * board without allocating anything.
 */
object BoardAnalysis {

    const val SIZE = Board.DEFAULT_SIZE
    private const val CELLS = SIZE * SIZE

    /**
     * A "hole" is an empty cell that has a filled cell somewhere *above* it
     * in the same column.
     *
     * In Block Blast pieces do not fall, so a hole is not automatically
     * fatal - you can still drop a piece into it. But every hole is dead
     * space that is hard to reuse, and an *enclosed* hole (filled both above
     * and below) is nearly impossible to fill again, so it is counted twice.
     */
    fun holes(mask: Long): Int {
        var count = 0
        for (c in 0 until SIZE) {
            var filledAbove = false
            for (r in 0 until SIZE) {
                val occupied = bit(mask, r, c) != 0L
                if (occupied) {
                    filledAbove = true
                } else if (filledAbove) {
                    count++ // empty with something above it
                    // enclosed if there is also something below it
                    for (b in r + 1 until SIZE) {
                        if (bit(mask, b, c) != 0L) {
                            count++
                            break
                        }
                    }
                }
            }
        }
        return count
    }

    /**
     * Number of 4-connected empty regions.
     *
     * A clean board is one big region; a board with lots of tiny separate
     * gaps is fragmented and will refuse pieces soon. This is a cheap and
     * very reliable fragmentation signal.
     */
    fun emptyComponents(mask: Long): Int {
        var remaining = (~mask) and FULL_MASK
        var components = 0
        // A plain stack of single-bit cell masks; 64 is the most it can ever need.
        val stack = LongArray(CELLS)
        while (remaining != 0L) {
            components++
            val seed = remaining and (-remaining)          // lowest set bit
            var size = 0
            stack[size++] = seed
            remaining = remaining and seed.inv()
            while (size > 0) {
                val cell = stack[--size]
                val r = cell / SIZE
                val c = cell % SIZE
                if (r > 0) {
                    val n = cell - SIZE
                    if (((remaining ushr n) and 1L) == 1L) {
                        remaining = remaining and (1L shl n).inv(); stack[size++] = 1L shl n
                    }
                }
                if (r < SIZE - 1) {
                    val n = cell + SIZE
                    if (((remaining ushr n) and 1L) == 1L) {
                        remaining = remaining and (1L shl n).inv(); stack[size++] = 1L shl n
                    }
                }
                if (c > 0) {
                    val n = cell - 1
                    if (((remaining ushr n) and 1L) == 1L) {
                        remaining = remaining and (1L shl n).inv(); stack[size++] = 1L shl n
                    }
                }
                if (c < SIZE - 1) {
                    val n = cell + 1
                    if (((remaining ushr n) and 1L) == 1L) {
                        remaining = remaining and (1L shl n).inv(); stack[size++] = 1L shl n
                    }
                }
            }
        }
        return components
    }

    /**
     * Fragmentation measured as "how many rectangles would you need to cover
     * all the empty space?".
     *
     * Greedy maximal-rectangle covering: repeatedly take the biggest empty
     * rectangle anchored at the top-left-most uncovered empty cell, mark it
     * covered, repeat. Lower is better - it rewards leaving open areas that
     * are shaped like one tidy rectangle rather than scattered confetti.
     */
    fun emptyRectangles(mask: Long): Int {
        val covered = BooleanArray(CELLS)
        var rectangles = 0
        while (true) {
            var anchor = -1
            for (i in 0 until CELLS) {
                if (covered[i]) continue
                if ((mask ushr i) and 1L == 0L) { anchor = i; break }
            }
            if (anchor < 0) break
            rectangles++
            val ar = anchor / SIZE
            val ac = anchor % SIZE
            // grow right
            var w = 0
            while (ac + w < SIZE && !covered[ar * SIZE + ac + w] && ((mask ushr (ar * SIZE + ac + w)) and 1L == 0L)) w++
            // grow down while the whole row segment stays empty+uncovered
            var h = 1
            while (ar + h < SIZE) {
                var fits = true
                for (c in 0 until w) {
                    val i = (ar + h) * SIZE + ac + c
                    if (covered[i] || ((mask ushr i) and 1L == 1L)) { fits = false; break }
                }
                if (!fits) break
                h++
            }
            for (r in 0 until h) for (c in 0 until w) covered[(ar + r) * SIZE + ac + c] = true
        }
        return rectangles
    }

    /**
     * Area of the largest all-empty rectangle.
     *
     * This is the "biggest hole I could still drop a big piece into" number.
     * A big value means the board still has breathing room.
     */
    fun largestEmptyRectangle(mask: Long): Int {
        val empty = ~mask and FULL_MASK
        val heights = IntArray(SIZE)
        var best = 0
        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {
                heights[c] = if (((empty ushr (r * SIZE + c)) and 1L) == 1L) heights[c] + 1 else 0
            }
            // largest rectangle in a histogram, one pass
            val stack = IntArray(SIZE + 1)
            var top = 0
            for (c in 0 until SIZE + 1) {
                val current = if (c < SIZE) heights[c] else 0
                while (top > 0 && heights[stack[top - 1]] >= current) {
                    val h = heights[stack[--top]]
                    val left = if (top > 0) stack[top - 1] else -1
                    val width = c - left - 1
                    if (h * width > best) best = h * width
                }
                stack[top++] = c
            }
        }
        return best
    }

    /**
     * "How likely am I to be stuck soon?" - the unplaceable-piece risk.
     *
     * Counts how many of the small shapes the game deals most often
     * ([PieceLibrary.lookAheadProbes]) can still be dropped on this board,
     * in any rotation. Low numbers mean the next round is in danger.
     * Returns 0 when every probe still fits.
     */
    fun unplaceableRisk(mask: Long, probes: List<Piece> = PieceLibrary.lookAheadProbes): Int {
        var blocked = 0
        for (probe in probes) {
            if (!canPlaceAnywhere(mask, probe)) blocked++
        }
        return blocked
    }

    /** True when [piece] fits on this board in at least one rotation/position. */
    fun canPlaceAnywhere(mask: Long, piece: Piece): Boolean {
        for (rotation in piece.rotations()) {
            for (row in 0..(SIZE - rotation.height)) {
                for (col in 0..(SIZE - rotation.width)) {
                    if (fits(mask, rotation, row, col)) return true
                }
            }
        }
        return false
    }

    /** True when [piece] can be dropped at ([row], [col]) without overlapping. */
    fun fits(mask: Long, piece: Piece, row: Int, col: Int): Boolean {
        if (row < 0 || col < 0) return false
        if (row + piece.height > SIZE || col + piece.width > SIZE) return false
        for ((dr, dc) in piece.offsets) {
            val bit = (row + dr) * SIZE + (col + dc)
            if (((mask ushr bit) and 1L) == 1L) return false
        }
        return true
    }

    /** Applies [piece] at ([row], [col]) to [mask]; caller must have checked [fits]. */
    fun place(mask: Long, piece: Piece, row: Int, col: Int): Long {
        var m = mask
        for ((dr, dc) in piece.offsets) m = m or (1L shl ((row + dr) * SIZE + (col + dc)))
        return m
    }

    /**
     * Removes every complete row and column from [mask].
     * @return the cleared mask, with the number of lines cleared as the second component.
     */
    fun clearLines(mask: Long): Pair<Long, Int> {
        val full = (1L shl SIZE) - 1L
        var lineMask = 0L
        for (r in 0 until SIZE) {
            if (((mask ushr (r * SIZE)) and full) == full) lineMask = lineMask or (full shl (r * SIZE))
        }
        for (c in 0 until SIZE) {
            var colBits = 0L
            for (r in 0 until SIZE) {
                if (((mask ushr (r * SIZE + c)) and 1L) == 1L) colBits = colBits or (1L shl r)
            }
            if (colBits == full) lineMask = lineMask or COLUMN_MASKS[c]
        }
        if (lineMask == 0L) return mask to 0
        return (mask and lineMask.inv()) to java.lang.Integer.bitCount(lineMask)
    }

    private val COLUMN_MASKS = LongArray(SIZE) { c ->
        var m = 0L
        for (r in 0 until SIZE) m = m or (1L shl (r * SIZE + c))
        m
    }

    private val FULL_MASK: Long = -1L ushr (64 - CELLS)

    private fun bit(mask: Long, r: Int, c: Int) = (mask ushr (r * SIZE + c)) and 1L

    /** Index of the lowest set bit, or -1 when the value is 0. */
    private fun Long.lowestOneBitIndex(): Int {
        if (this == 0L) return -1
        return java.lang.Long.numberOfTrailingZeros(this)
    }
}