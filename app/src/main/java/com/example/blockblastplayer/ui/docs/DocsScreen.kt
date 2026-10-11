package com.example.blockblastplayer.ui.docs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * "How it works" - the short explainer the brief asks for.
 *
 * This text is deliberately written so it can be read out in a presentation:
 * it is the same story as the README, in a form that fits on a phone.
 */
@Composable
fun DocsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("How it works", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        Section("The idea") {
            Text(
                "Block Blast is an 8x8 grid. Each round you get three blocks " +
                    "and must drop all three. When a full row or column is " +
                    "made, it disappears and scores points. The game loses " +
                    "when none of the three blocks fit anywhere.\n\n" +
                    "This app answers one question: given the board and the " +
                    "three blocks, what is the best order and position for " +
                    "each one? It shows you the answer. You place the blocks."
            )
        }

        Section("1. Capture") {
            Text(
                "The app asks for the standard Android screen-capture " +
                    "permission (MediaProjection). Each frame is kept in " +
                    "memory for a few hundred milliseconds and thrown away. " +
                    "Nothing is stored and there is no network code in the " +
                    "project at all."
            )
        }

        Section("2. Detect the grid") {
            Text(
                "The board is found by flood fill. The gaps between the " +
                    "cells are a dark navy that forms one connected network " +
                    "across the whole 8x8 panel, so the bounding box of the " +
                    "largest connected run of that colour is the panel - " +
                    "whether the board is empty or almost full.\n\n" +
                    "Each cell is then sampled at its centre (the median of " +
                    "five nearby pixels, to ignore JPEG noise). A cell counts " +
                    "as filled when its colour is far enough from the " +
                    "measured empty-cell colour."
            )
        }

        Section("3. Read the pieces") {
            Text(
                "The three tray pieces sit below the board. They are found by " +
                    "colour (they are never the same colour as the " +
                    "background), grouped left-to-right into three slots, and " +
                    "snapped onto a grid to recover their shape."
            )
        }

        Section("4. Solve") {
            Text(
                "The solver tries every ordering of the three blocks (6 orders) " +
                    "and, for each order, every legal position of each block in " +
                    "every rotation. After each placement it immediately " +
                    "resolves line clears, exactly like the game.\n\n" +
                    "That is at most 6 x 64 x 64 x 64 = 1.5 million complete " +
                    "sequences. Small enough to search exhaustively on a " +
                    "phone, which means the answer is optimal under the " +
                    "heuristic rather than a guess. A time budget stops the " +
                    "search early if the phone is slow; you always still get a " +
                    "legal move."
            )
        }

        Section("5. Score it") {
            Text(
                "Each finished position gets one number from a weighted sum:",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            CodeBlock(
                """
                score = +100 per line cleared
                      + 60  extra for a multi-line blast (squared)
                      + 25  per extra step of a combo
                      - 12  per hole
                      -  8  per separate empty region
                      +  6  x sqrt(largest empty rectangle)
                      - 18  per common shape that can no longer fit
                      -400  if the board ends the round completely full
                """.trimIndent()
            )
        }

        Section("Why these terms") {
            Text(
                "Lines are the only thing that really scores, so they " +
                    "dominate. A hole is an empty cell with a block above it: " +
                    "dead space that is hard to reuse, so it is punished " +
                    "twice if it is also sealed underneath. Separate empty " +
                    "regions mean a scattered board that will refuse blocks " +
                    "soon. The largest empty rectangle rewards keeping one " +
                    "roomy area instead of lots of small gaps. The last term " +
                    "asks \"how many common small blocks still fit?\" - if " +
                    "most do not, the next round is in danger."
            )
        }

        Section("Why it is drawn, not played") {
            Text(
                "The overlay is a plain drawing layer with FLAG_NOT_TOUCHABLE, " +
                    "so taps pass straight through to the game. The app " +
                    "contains no input-injection code at all. That is on " +
                    "purpose: automating this game breaks its rules, and for a " +
                    "project about algorithms the interesting part is the " +
                    "reasoning, not the tapping."
            )
        }

        Section("Honest limits") {
            Text(
                "- It only understands the normal 8x8 layout.\n" +
                    "- Very high score bars, boosters or popups over the " +
                    "board can confuse detection; turn extra overlays off.\n" +
                    "- A floating \"FPS\" badge on top of the board is read as " +
                    "a filled cell - remove it before a demo.\n" +
                    "- The tray reader assumes pieces are a solid colour.\n" +
                    "- The heuristic is good, not provably optimal for the " +
                    "game's own scoring, because the real scoring rules for " +
                    "combos are not fully published."
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    content()
}

@Composable
private fun CodeBlock(text: String) {
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF10182B), RoundedCornerShape(8.dp))
            .padding(12.dp)
    )
}