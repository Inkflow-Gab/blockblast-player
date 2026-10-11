package com.example.blockblastplayer.solver

/**
 * The tuning knobs of the solver.
 *
 * Every term of the heuristic gets one number. The defaults were chosen by
 * hand so the relative importance reads clearly:
 *
 *  * clearing lines is by far the most valuable thing you can do,
 *  * combos are valuable because they multiply that score in the real game,
 *  * holes and fragmentation are the main way you lose a game,
 *  * free space and look-ahead risk are the tie-breakers between two moves
 *    that clear the same number of lines.
 *
 * Every field is public and the [Heuristic] object is immutable, so the UI
 * can show them (and the docs screen can explain them) without any plumbing.
 */
data class HeuristicWeights(
    /** Points per line cleared. */
    val perLine: Double = 100.0,
    /** Bonus for clearing several lines with a single piece. */
    val multiLineBonus: Double = 60.0,
    /** Extra for each consecutive placement that also cleared a line. */
    val comboBonus: Double = 25.0,
    /** Penalty per hole (an empty cell with a block above it). */
    val perHole: Double = 12.0,
    /** Penalty per disconnected empty region. */
    val perFragment: Double = 8.0,
    /** Reward for the largest empty rectangle (square-rooted: big but diminishing). */
    val freeSpaceBonus: Double = 6.0,
    /** Penalty per small shape that can no longer be placed at all. */
    val perRisk: Double = 18.0,
    /** Penalty for ending the round with a completely full board. */
    val boardFullPenalty: Double = 400.0
)

/** Turns a finished position into a single number we can compare. */
object Heuristic {

    /**
     * @param mask           occupancy of the board *after* all three pieces
     * @param totalLines     lines cleared across the whole round
     * @param bestSingleMove most lines cleared by one placement (rewards combos)
     * @param streak         longest run of consecutive line-clearing placements
     */
    fun evaluate(
        mask: Long,
        totalLines: Int,
        bestSingleMove: Int,
        streak: Int,
        weights: HeuristicWeights = HeuristicWeights()
    ): ScoreBreakdown {
        val holeCount = BoardAnalysis.holes(mask)
        val fragments = BoardAnalysis.emptyComponents(mask)
        val free = BoardAnalysis.largestEmptyRectangle(mask)
        val risk = BoardAnalysis.unplaceableRisk(mask)

        var score = 0.0
        score += totalLines * weights.perLine
        // clearing 2+ lines at once is worth much more than two separate lines
        if (bestSingleMove > 1) {
            score += (bestSingleMove - 1) * weights.multiLineBonus * (bestSingleMove - 1)
        }
        score += (streak - 1).coerceAtLeast(0) * weights.comboBonus

        score -= holeCount * weights.perHole
        score -= fragments * weights.perFragment
        score += kotlin.math.sqrt(free.toDouble()) * weights.freeSpaceBonus
        score -= risk * weights.perRisk

        val empty = java.lang.Long.bitCount(mask.inv())
        if (empty == 0) score -= weights.boardFullPenalty

        return ScoreBreakdown(
            lines = totalLines,
            combo = streak,
            holes = holeCount,
            fragments = fragments,
            freeSpace = free,
            risk = risk,
            total = score
        )
    }
}