package com.example.blockblastplayer.solver

/**
 * An 8x8 Block Blast board.
 *
 * The board is stored twice:
 *  - [cells] a plain `BooleanArray` used by everything readable (tests, UI)
 *  - [mask] a 64-bit mask (`bit r * 8 + c`) used by the hot path in the
 *    solver, where we clear lines thousands of times per solve.
 *
 * Keeping both means the solver stays fast while the code that is *read* by
 * a human stays obvious. There is no Android dependency in this file, so it
 * runs on a normal JVM in the unit tests.
 */
class Board(val size: Int = DEFAULT_SIZE) {

    private val cells = BooleanArray(size * size)
    private var mask: Long = 0L
    private var filled: Int = 0

    init {
        require(size == DEFAULT_SIZE) {
            "Block Blast is played on an $DEFAULT_SIZE x $DEFAULT_SIZE grid, got ${size}x${size}"
        }
    }

    fun isFilled(row: Int, col: Int): Boolean = cells[row * size + col]

    fun set(row: Int, col: Int, value: Boolean) {
        val i = row * size + col
        if (cells[i] == value) return
        cells[i] = value
        val bit = 1L shl i
        if (value) {
            mask = mask or bit
            filled++
        } else {
            mask = mask and bit.inv()
            filled--
        }
    }

    /** Number of filled cells currently on the board. */
    fun filledCount(): Int = filled

    /** Number of empty cells. */
    fun emptyCount(): Int = size * size - filled

    /** Raw 64-bit occupancy mask, useful for fast set operations. */
    fun mask(): Long = mask

    fun copy(): Board = Board(size).also { it.loadFrom(this) }

    fun copyInto(target: Board) {
        target.cells.indices.forEach { target.cells[it] = cells[it] }
        target.mask = mask
        target.filled = filled
    }

    private fun loadFrom(other: Board) {
        other.cells.indices.forEach { cells[it] = other.cells[it] }
        mask = other.mask
        filled = other.filled
    }

    /** Restores the board to completely empty. */
    fun clear() {
        cells.fill(false)
        mask = 0L
        filled = 0
    }

    /**
     * Removes every completely filled row and column, exactly like the real
     * game does: rows and columns that are complete *at the same time* are
     * all cleared together.
     *
     * @return how many lines were cleared (rows + columns).
     */
    fun clearCompletedLines(): Int {
        val full = (1L shl size) - 1L
        var lineMask = 0L
        for (r in 0 until size) {
            val rowBits = (mask ushr (r * size)) and full
            if (rowBits == full) lineMask = lineMask or (full shl (r * size))
        }
        for (c in 0 until size) {
            var colBits = 0L
            for (r in 0 until size) if (isFilled(r, c)) colBits = colBits or (1L shl r)
            if (colBits == full) lineMask = lineMask or COLUMN_MASKS[c]
        }
        if (lineMask == 0L) return 0
        mask = mask and lineMask.inv()
        filled -= java.lang.Integer.bitCount(lineMask)
        // keep the boolean view in sync with the mask
        for (i in cells.indices) cells[i] = (mask ushr i) and 1L == 1L
        return java.lang.Integer.bitCount(lineMask)
    }

    /** Rows (as a list) that are currently complete. */
    fun completeRows(): List<Int> {
        val full = (1L shl size) - 1L
        return (0 until size).filter { r -> (mask ushr (r * size)) and full == full }
    }

    /** Columns (as a list) that are currently complete. */
    fun completeColumns(): List<Int> {
        val full = (1L shl size) - 1L
        return (0 until size).filter { c ->
            var colBits = 0L
            for (r in 0 until size) if (isFilled(r, c)) colBits = colBits or (1L shl r)
            colBits == full
        }
    }

    /** Renders the board as text, handy in tests and debug output. */
    fun render(): String = buildString {
        for (r in 0 until size) {
            for (c in 0 until size) append(if (isFilled(r, c)) '#' else '.')
            append('\n')
        }
    }

    override fun toString(): String = render().trimEnd()

    companion object {
        const val DEFAULT_SIZE = 8

        private val COLUMN_MASKS = LongArray(DEFAULT_SIZE) { c ->
            var m = 0L
            for (r in 0 until DEFAULT_SIZE) m = m or (1L shl (r * DEFAULT_SIZE + c))
            m
        }

        /**
         * Builds a board from 8 strings of 8 characters.
         * '#' or '1' means filled, anything else means empty.
         */
        fun parse(rows: List<String>): Board {
            require(rows.size == DEFAULT_SIZE) { "expected $DEFAULT_SIZE rows, got ${rows.size}" }
            val b = Board()
            rows.forEachIndexed { r, line ->
                require(line.length == DEFAULT_SIZE) {
                    "row $r must have $DEFAULT_SIZE characters, got ${line.length}"
                }
                line.forEachIndexed { c, ch ->
                    if (ch == '#' || ch == '1') b.set(r, c, true)
                }
            }
            return b
        }

        fun parse(vararg rows: String): Board = parse(rows.toList())

        fun empty(): Board = Board()
    }
}