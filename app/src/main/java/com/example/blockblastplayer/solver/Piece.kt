package com.example.blockblastplayer.solver

/**
 * A block: a small boolean matrix, e.g. the "T" tetromino below.
 *
 * ```
 *  .#.
 *  ###
 * ```
 *
 * Pieces are stored *trimmed* (no completely empty border row or column) so
 * that (0,0) is always a real block of the piece. That keeps placement maths
 * simple: dropping the piece at (row, col) means its top-left block lands on
 * board cell (row, col).
 */
class Piece private constructor(
    val name: String,
    private val cells: List<List<Boolean>>
) {
    val height: Int get() = cells.size
    val width: Int get() = cells[0].size

    fun filled(r: Int, c: Int): Boolean = cells[r][c]

    fun isFilled(r: Int, c: Int): Boolean =
        r in cells.indices && c in cells[0].indices && cells[r][c]

    /** All occupied offsets inside the piece. */
    val offsets: List<Offset> by lazy {
        buildList {
            for (r in 0 until height) for (c in 0 until width) if (cells[r][c]) add(Offset(r, c))
        }
    }

    val size: Int get() = offsets.size

    /** All distinct rotations of the piece (clockwise), original first. */
    fun rotations(): List<Piece> {
        val result = mutableListOf(this)
        var current = this
        repeat(3) {
            current = current.rotated()
            if (result.none { it.sameShapeAs(current) }) result += current
        }
        return result
    }

    private fun rotated(): Piece {
        val out = Array(width) { BooleanArray(height) }
        for (r in 0 until height) for (c in 0 until width) out[c][height - 1 - r] = cells[r][c]
        return Piece(name, out.map { it.toList() })
    }

    private fun sameShapeAs(other: Piece): Boolean =
        height == other.height && width == other.width &&
            cells.indices.all { r -> cells[r].indices.all { c -> cells[r][c] == other.cells[r][c] } }

    /** ASCII art, e.g. ".\n#." - used by the shape picker and tests. */
    fun render(): String = cells.joinToString("\n") { row -> row.joinToString("") { if (it) "#" else "." } }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Piece) return false
        if (name != other.name) return false
        if (height != other.height || width != other.width) return false
        return cells.indices.all { r ->
            cells[r].indices.all { c -> cells[r][c] == other.cells[r][c] }
        }
    }

    override fun hashCode(): Int {
        var h = name.hashCode()
        for (row in cells) for (v in row) h = h * 31 + if (v) 1 else 0
        return h
    }

    override fun toString(): String = "$name(${width}x$height)"

    data class Offset(val row: Int, val col: Int)

    companion object {
        /**
         * Creates a piece from ASCII art, e.g. `of("T", ".#.", "###")`.
         * Blank border rows/columns are removed automatically.
         */
        fun of(name: String, vararg rows: String): Piece {
            require(rows.isNotEmpty()) { "a piece needs at least one row" }
            val w = rows.maxOf { it.length }
            val grid = rows.map { row ->
                BooleanArray(w) { c -> c < row.length && (row[c] == '#' || row[c] == '1') }
            }
            return Piece(name, trim(grid).map { it.toList() })
        }

        private fun trim(grid: List<BooleanArray>): List<BooleanArray> {
            var top = 0
            var bottom = grid.size - 1
            var left = 0
            var right = (grid.first().size - 1)
            while (top <= bottom && grid[top].none { it }) top++
            while (bottom >= top && grid[bottom].none { it }) bottom--
            while (left <= right && grid.none { it[left] }) left++
            while (right >= left && grid.none { it[right] }) right--
            if (top > bottom) return listOf(BooleanArray(1) { true })
            return (top..bottom).map { r -> grid[r].copyOfRange(left, right + 1) }
        }
    }
}