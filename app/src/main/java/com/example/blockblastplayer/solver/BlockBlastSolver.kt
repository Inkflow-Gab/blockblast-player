package com.example.blockblastplayer.solver

/**
 * Finds the best way to play a round of three pieces on an 8x8 Block Blast
 * board.
 *
 * ## How it searches
 *
 * A round is a *sequence*: three placements, in some order. The solver
 *
 *  1. enumerates every ordering of the three pieces (3! = 6 orders),
 *  2. for each order walks the board and tries every legal position of the
 *     next piece, in every rotation (at most 64 positions),
 *  3. applies the placement and immediately resolves line clears,
 *  4. recurses until all three pieces are used,
 *  5. scores the resulting position and keeps the best one.
 *
 * The branch factor is bounded by 64 and the depth is only 3, so the whole
 * space is at most 6 x 64^3 = 1.5M leaves - small enough to search
 * exhaustively on a phone. The answer is therefore *optimal under the
 * heuristic*, not merely "a decent move".
 *
 * ## Why scoring is done in two stages
 *
 * Some heuristic terms are almost free (lines cleared, combo streak - they
 * are accumulated while we search). Others are not: the "can the next round
 * still be placed" look-ahead probes every small shape in every rotation on
 * every square, which is thousands of operations. Running that on all 1.5M
 * leaves would take minutes.
 *
 * So the search runs in two passes over the *same* exhaustive tree:
 *
 *  - **Pass 1** keeps, for every leaf, the cheap terms only and retains a
 *    shortlist of the best [Options.shortlistSize] leaves.
 *  - **Pass 2** runs the expensive board analysis on just that shortlist and
 *    picks the overall winner.
 *
 * Both passes still consider every ordering and every legal placement, so
 * nothing is skipped - only the *scoring* is ordered from cheap to
 * expensive, which is what keeps the app responsive.
 *
 * ## Independence from Android
 *
 * Nothing in this class (or in [Board], [Piece], [BoardAnalysis],
 * [Heuristic]) imports an Android type, so [solve] runs directly in plain JVM
 * unit tests.
 */
class BlockBlastSolver(
    private val weights: HeuristicWeights = HeuristicWeights()
) {

    data class Options(
        /** Abort after this many scored sequences; the best-so-far is returned. */
        val maxNodes: Long = DEFAULT_MAX_NODES,
        /**
         * Give up after this many milliseconds so the UI never stalls.
         * The search stops at the next leaf boundary and returns the best
         * sequence found so far, which is still a legal, valid move.
         */
        val timeBudgetMs: Long = DEFAULT_TIME_BUDGET_MS,
        /** How many leaves survive the cheap pass to get the full analysis. */
        val shortlistSize: Int = DEFAULT_SHORTLIST
    )

    /**
     * @param board  current 8x8 position; not mutated
     * @param pieces the three pieces of this round, in tray order
     */
    fun solve(board: Board, pieces: List<Piece>, options: Options = Options()): SolveResult {
        require(pieces.isNotEmpty()) { "need at least one piece" }
        val startMask = board.mask()

        // ---- pass 1: exhaustive search, cheap score, keep a shortlist -----
        val shortlist = ArrayList<Candidate>(options.shortlistSize)
        var nodes = 0L
        var truncated = false

        // Deadline check: reading the clock is far too slow to do at every
        // leaf, so we only look at it every TIME_CHECK_STRIDE sequences.
        val startedAt = System.nanoTime()
        val deadlineNanos = options.timeBudgetMs * 1_000_000L
        /** Set when we run out of time or nodes; the DFS unwinds on the next check. */
        var aborted = false

        // working buffers reused across the whole search
        val chosen = arrayOfNulls<Piece>(pieces.size)
        val chosenRow = IntArray(pieces.size)
        val chosenCol = IntArray(pieces.size)
        val clearedEach = IntArray(pieces.size)
        val orderBuf = IntArray(pieces.size)

        for (order in permutations(pieces.indices.toList())) {
            for (i in pieces.indices) orderBuf[i] = order[i]

            fun dfs(depth: Int, mask: Long, totalLines: Int, streak: Int, bestSingle: Int) {
                if (aborted) return
                if (depth == pieces.size) {
                    nodes++
                    if (nodes % TIME_CHECK_STRIDE == 0L &&
                        System.nanoTime() - startedAt > deadlineNanos
                    ) {
                        truncated = true
                        aborted = true
                        return
                    }
                    val leafStreak = if (streak > 0) streak else 1
                    val cheap = cheapScore(totalLines, bestSingle, leafStreak)
                    val candidate = Candidate(
                        order = orderBuf.copyOf(),
                        rows = chosenRow.copyOf(),
                        cols = chosenCol.copyOf(),
                        cleared = clearedEach.copyOf(),
                        pieces = chosen.copyOf(),
                        mask = mask,
                        totalLines = totalLines,
                        bestSingle = bestSingle,
                        streak = leafStreak
                    )
                    insertIntoShortlist(shortlist, candidate, cheap, options.shortlistSize)
                    return
                }
                val piece = pieces[orderBuf[depth]]
                for (rotation in rotationsOf(piece)) {
                    val lastRow = Board.DEFAULT_SIZE - rotation.height
                    val lastCol = Board.DEFAULT_SIZE - rotation.width
                    for (row in 0..lastRow) {
                        for (col in 0..lastCol) {
                            if (aborted) return
                            if (!BoardAnalysis.fits(mask, rotation, row, col)) continue
                            val withPiece = BoardAnalysis.place(mask, rotation, row, col)
                            val (afterClear, lines) = BoardAnalysis.clearLines(withPiece)
                            chosen[depth] = rotation
                            chosenRow[depth] = row
                            chosenCol[depth] = col
                            clearedEach[depth] = lines
                            dfs(
                                depth + 1,
                                afterClear,
                                totalLines + lines,
                                if (lines > 0) streak + 1 else 0,
                                maxOf(bestSingle, lines)
                            )
                            if (nodes >= options.maxNodes) {
                                truncated = true
                                aborted = true
                                return
                            }
                        }
                    }
                }
                // if nothing was placed the branch simply is not explored
            }

            dfs(0, startMask, 0, 0, 0)
            if (truncated) break
        }

        // ---- pass 2: full (expensive) analysis on the shortlist ----------
        var best: Candidate? = null
        var bestBreakdown: ScoreBreakdown? = null
        var bestTotal = Double.NEGATIVE_INFINITY
        for (candidate in shortlist) {
            val breakdown = Heuristic.evaluate(
                mask = candidate.mask,
                totalLines = candidate.totalLines,
                bestSingleMove = candidate.bestSingle,
                streak = candidate.streak,
                weights = weights
            )
            // deterministic tie-break: earlier order / earlier position wins
            if (breakdown.total > bestTotal ||
                (breakdown.total == bestTotal && tieBreakBetter(candidate, best))
            ) {
                bestTotal = breakdown.total
                best = candidate
                bestBreakdown = breakdown
            }
        }

        val winner = best
        val moves = winner?.let {
            it.order.mapIndexed { depth, pieceIdx ->
                val piece = it.pieces[depth]!!
                Move(
                    pieceIndex = pieceIdx,
                    row = it.rows[depth],
                    col = it.cols[depth],
                    linesCleared = it.cleared[depth],
                    cells = piece.offsets.map { o -> Cell(it.rows[depth] + o.row, it.cols[depth] + o.col) }
                )
            }
        } ?: emptyList()

        return SolveResult(
            moves = moves,
            score = if (bestTotal == Double.NEGATIVE_INFINITY) 0.0 else bestTotal,
            breakdown = bestBreakdown ?: ScoreBreakdown(0, 0, 0, 0, 0, 0, 0.0),
            evaluatedSequences = nodes,
            truncated = truncated
        )
    }

    /** Like [solve] but returns null when the pieces do not fit at all. */
    fun solveOrNull(board: Board, pieces: List<Piece>, options: Options = Options()): SolveResult? {
        val result = solve(board, pieces, options)
        return if (result.isEmpty) null else result
    }

    /**
     * The cheap part of the score: only the terms we already know while
     * searching, so it costs nothing at each leaf.
     */
    private fun cheapScore(totalLines: Int, bestSingle: Int, streak: Int): Double {
        var s = totalLines * weights.perLine
        if (bestSingle > 1) s += (bestSingle - 1) * weights.multiLineBonus * (bestSingle - 1)
        s += (streak - 1).coerceAtLeast(0) * weights.comboBonus
        return s
    }

    /** Keeps the [limit] highest-scoring candidates; ties resolved by order/position. */
    private fun insertIntoShortlist(
        list: ArrayList<Candidate>,
        candidate: Candidate,
        cheap: Double,
        limit: Int
    ) {
        if (limit <= 0) return
        if (list.size == limit) {
            val worstIdx = worstIndexOf(list)
            val worst = list[worstIdx]
            if (cheap < worst.cheap) return
            if (cheap == worst.cheap && !tieBreakBetter(candidate, worst)) return
            list.removeAt(worstIdx)
        }
        list.add(candidate.apply { this.cheap = cheap })
    }

    private fun worstIndexOf(list: ArrayList<Candidate>): Int {
        var worstIdx = 0
        for (i in list.indices) {
            val c = list[i]
            val w = list[worstIdx]
            if (c.cheap < w.cheap || (c.cheap == w.cheap && tieBreakBetter(w, c))) worstIdx = i
        }
        return worstIdx
    }

    /**
     * Deterministic ordering so the same input always gives the same answer:
     * lower piece index order first, then lower row, then lower column.
     */
    private fun tieBreakBetter(a: Candidate?, b: Candidate?): Boolean {
        if (a == null) return false
        if (b == null) return true
        for (i in a.order.indices) {
            if (a.order[i] != b.order[i]) return a.order[i] < b.order[i]
        }
        for (i in a.rows.indices) {
            if (a.rows[i] != b.rows[i]) return a.rows[i] < b.rows[i]
            if (a.cols[i] != b.cols[i]) return a.cols[i] < b.cols[i]
        }
        return false
    }

    private val rotationCache = HashMap<Piece, List<Piece>>()

    private fun rotationsOf(piece: Piece): List<Piece> =
        rotationCache.getOrPut(piece) { piece.rotations() }

    private fun permutations(input: List<Int>): List<List<Int>> {
        if (input.size <= 1) return listOf(input)
        val result = ArrayList<List<Int>>()
        for (i in input.indices) {
            val rest = input.toMutableList().apply { removeAt(i) }
            for (p in permutations(rest)) result.add(listOf(input[i]) + p)
        }
        return result
    }

    /** A leaf of the search tree: a full placement sequence. */
    private class Candidate(
        val order: IntArray,
        val rows: IntArray,
        val cols: IntArray,
        val cleared: IntArray,
        val pieces: Array<Piece?>,
        val mask: Long,
        val totalLines: Int,
        val bestSingle: Int,
        val streak: Int
    ) {
        var cheap: Double = 0.0
    }

    companion object {
        const val MAX_PIECES = 3
        const val DEFAULT_MAX_NODES = 3_000_000L
        const val DEFAULT_SHORTLIST = 256

        /**
         * Long enough for the worst case (a near-empty board, where the
         * tree is biggest) on a slow phone, short enough that the UI never
         * feels frozen.
         */
        const val DEFAULT_TIME_BUDGET_MS = 1_200L

        /** Only read the clock every this many sequences. */
        private const val TIME_CHECK_STRIDE = 2_048L
    }
}