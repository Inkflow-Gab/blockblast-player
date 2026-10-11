package com.example.blockblastplayer.solver

/**
 * The shapes Block Blast hands out.
 *
 * The list is a convenience for the manual mode (a shape picker) and for the
 * solver's "can the next piece still be placed" look-ahead. The solver itself
 * never assumes a piece comes from this list - it only needs a [Piece].
 *
 * Shapes are written in ASCII so the grid lines up in the source file.
 */
object PieceLibrary {

    val MONOMINO: Piece = Piece.of("Mono", "#")

    val DOMINO_H: Piece = Piece.of("Domino", "##")
    val DOMINO_V: Piece = Piece.of("Domino", "#", "#")

    val TRI_I_H: Piece = Piece.of("Bar3", "###")
    val TRI_I_V: Piece = Piece.of("Bar3", "#", "#", "#")

    val TRI_L: Piece = Piece.of("Corner", "##", "#.")
    val TRI_L_R: Piece = Piece.of("Corner", "##", ".#")
    val TRI_L_D: Piece = Piece.of("Corner", "#.", "##")
    val TRI_L_U: Piece = Piece.of("Corner", ".#", "##")

    val SQUARE_2: Piece = Piece.of("Square", "##", "##")

    val TET_I_H: Piece = Piece.of("Bar4", "####")
    val TET_I_V: Piece = Piece.of("Bar4", "#", "#", "#", "#")

    val TET_T_D: Piece = Piece.of("T", ".#.", "###")
    val TET_T_U: Piece = Piece.of("T", "###", ".#.")
    val TET_T_R: Piece = Piece.of("T", ".#", "##", ".#")
    val TET_T_L: Piece = Piece.of("T", "#.", "##", "#.")

    val TET_L: Piece = Piece.of("L", "#..", "###")
    val TET_L_R: Piece = Piece.of("L", "..#", "###")
    val TET_L_U: Piece = Piece.of("L", "###", "#..")
    val TET_L_D: Piece = Piece.of("L", "###", "..#")

    val TET_S: Piece = Piece.of("S", ".##", "##.")
    val TET_Z: Piece = Piece.of("Z", "##.", ".##")

    val PENT_I_H: Piece = Piece.of("Bar5", "#####")
    val PENT_I_V: Piece = Piece.of("Bar5", "#", "#", "#", "#", "#")

    val PENT_U: Piece = Piece.of("U", "#.#", "###")
    val PENT_U_R: Piece = Piece.of("U", "##", "#.", "##")
    val PENT_L: Piece = Piece.of("BigL", "#..", "#..", "###")
    val PENT_T: Piece = Piece.of("BigT", ".#.", ".#.", "###")
    val PENT_X: Piece = Piece.of("Plus", ".#.", "###", ".#.")
    val PENT_W: Piece = Piece.of("W", "#..", "##.", ".##")

    /**
     * Every distinct piece the picker offers. Rotations are added by the
     * solver on demand, so this list holds one orientation per shape.
     */
    val all: List<Piece> = listOf(
        MONOMINO,
        DOMINO_H, DOMINO_V,
        TRI_I_H, TRI_L, TRI_L_R,
        SQUARE_2,
        TET_I_H, TET_T_D, TET_L, TET_L_R, TET_S, TET_Z,
        PENT_I_H, PENT_U, PENT_L, PENT_T, PENT_X, PENT_W,
    )

    /**
     * Small, common shapes used as "what might the next round look like"
     * when the solver estimates how risky the final position is.
     *
     * Uses only 1-3 blocks: those are the pieces the game deals most often
     * and they are the hardest ones to fit into a cramped board.
     */
    val lookAheadProbes: List<Piece> = listOf(
        MONOMINO,
        DOMINO_H, DOMINO_V,
        TRI_I_H, TRI_I_V,
        TRI_L, TRI_L_D,
        SQUARE_2,
        TET_T_D,
    )

    /** Looks a piece up by display name, used when restoring saved state. */
    fun byName(name: String): Piece? = all.firstOrNull { it.name == name }
}