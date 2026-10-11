package com.example.blockblastplayer.ui.manual

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.blockblastplayer.solver.BlockBlastSolver
import com.example.blockblastplayer.solver.Board
import com.example.blockblastplayer.solver.Cell
import com.example.blockblastplayer.solver.Move
import com.example.blockblastplayer.solver.Piece
import com.example.blockblastplayer.solver.PieceLibrary
import com.example.blockblastplayer.solver.SolveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manual mode: the screen that makes the demo work without any permissions.
 *
 * You tap the board cells yourself, pick three shapes, press Solve, and the
 * app shows the three moves with numbered badges - exactly like the floating
 * overlay does, but on a board the app draws itself. This is the screen to
 * use on a school projector, and the fallback when screen capture is refused.
 */
@Composable
fun ManualModeScreen(modifier: Modifier = Modifier) {
    // The editable board the user is drawing.
    val cells = remember { mutableStateListOf(*Array(64) { false }) }
    // Which piece is currently being edited, and its ASCII art.
    val slotShapes = remember { mutableStateListOf<String?>(null, null, null) }
    var selectedSlot by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<SolveResult?>(null) }
    var status by remember { mutableStateOf("Tap cells to fill the board, then press Solve.") }
    var busy by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Manual board", style = MaterialTheme.typography.titleLarge)
        Text(
            "Type in a position by hand. Nothing is captured or sent anywhere - " +
                "this screen works with every permission switched off.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))

        BoardGrid(
            filled = cells.toList(),
            // cells that the solution will fill, coloured by step number
            highlights = result?.moves?.let { moves -> moves.flatMapIndexed { i, m -> m.cells.map { it to i } } }
                ?: emptyList(),
            onCellClick = { r, c ->
                cells[r * 8 + c] = !cells[r * 8 + c]
                result = null
            }
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { cells.indices.forEach { cells[it] = false }; result = null }) {
                Text("Clear board")
            }
            OutlinedButton(onClick = {
                cells.indices.forEach { i -> cells[i] = randomPattern() }
                result = null
            }) { Text("Random board") }
            Button(
                enabled = !busy,
                onClick = {
                    val pieces = slotShapes.map { art ->
                        art?.let { artToPiece(it) }
                    }
                    if (pieces.any { it == null }) {
                        status = "Choose a shape for all three tray slots first."
                    } else {
                        busy = true
                        status = "Searching every order and every legal position..."
                        scope.launch {
                            // never block the UI thread - the search is the
                            // one heavy thing this app does
                            val board = Board().also { b ->
                                for (i in 0 until 64) if (cells[i]) b.set(i / 8, i % 8, true)
                            }
                            val res = withContext(Dispatchers.Default) {
                                BlockBlastSolver().solve(board, pieces.filterNotNull())
                            }
                            result = res
                            busy = false
                            status = if (res.moves.isEmpty()) {
                                "Those pieces do not all fit. Try different shapes."
                            } else {
                                "Checked ${res.evaluatedSequences} complete sequences " +
                                    "in ${if (res.truncated) "the time budget" else "full"}."
                            }
                        }
                    }
                }
            ) { Text(if (busy) "Solving..." else "Solve") }
        }

        Spacer(Modifier.height(12.dp))
        statusText(status)

        result?.let { res ->
            if (res.moves.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Suggested order", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                res.moves.forEachIndexed { i, move ->
                    MoveRow(i + 1, move, res)
                }
                Spacer(Modifier.height(12.dp))
                ScoreCard(res)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Tray pieces", style = MaterialTheme.typography.titleMedium)
        Text("Pick a shape for slot 1, 2 and 3. They can be the same.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { slot ->
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedSlot = slot },
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        if (selectedSlot == slot) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                ) {
                    Column(
                        Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Slot ${slot + 1}", style = MaterialTheme.typography.labelMedium)
                        val art = slotShapes[slot]
                        if (art == null) {
                            Box(
                                Modifier.size(56.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("?", style = MaterialTheme.typography.titleLarge) }
                        } else {
                            MiniPiece(art)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        PiecePicker { art ->
            slotShapes[selectedSlot] = art
            result = null
            if (selectedSlot < 2) selectedSlot += 1
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun statusText(status: String) {
    Text(status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MoveRow(step: Int, move: Move, result: SolveResult) {
    val colours = listOf(Color(0xFF4FC3F7), Color(0xFFFFB74D), Color(0xFF81C784))
    val colour = colours[(step - 1) % colours.size]
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).background(colour, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("$step", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    "Piece ${move.pieceIndex + 1} -> row ${move.row + 1}, col ${move.col + 1}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (move.linesCleared > 0) "clears ${move.linesCleared} line(s)" else "no line clear",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun ScoreCard(res: SolveResult) {
    val b = res.breakdown
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Why this order", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    appendLine("score          ${"%.1f".format(res.score)}")
                    appendLine("lines cleared  ${b.lines}")
                    appendLine("best combo     ${b.combo}")
                    appendLine("holes          ${b.holes}")
                    appendLine("empty regions  ${b.fragments}")
                    appendLine("free space     ${b.freeSpace}")
                    appendLine("dead-end risk  ${b.risk} / 9 shapes")
                },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** The interactive 8x8 grid. */
@Composable
fun BoardGrid(
    filled: List<Boolean>,
    highlights: List<Pair<Cell, Int>> = emptyList(),
    onCellClick: ((Int, Int) -> Unit)? = null
) {
    val colours = listOf(Color(0xFF4FC3F7), Color(0xFFFFB74D), Color(0xFF81C784))
    val highlightByIndex = highlights.associate { it.first to it.second }
    Card(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(8.dp)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(Board.DEFAULT_SIZE),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                userScrollEnabled = false
            ) {
                items(64) { i ->
                    val r = i / 8
                    val c = i % 8
                    val cell = Cell(r, c)
                    val step = highlightByIndex[cell]
                    val base = if (filled.getOrElse(i) { false })
                        Color(0xFF7E9CC6)
                    else Color(0xFF232C55)
                    Box(
                        Modifier
                            .padding(1.dp)
                            .aspectRatio(1f)
                            .background(
                                if (step != null) colours[step % colours.size] else base,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (step != null) Color.Black else Color(0xFF2E3760),
                                RoundedCornerShape(4.dp)
                            )
                            .then(
                                if (onCellClick != null)
                                    Modifier.clickable { onCellClick(r, c) }
                                else Modifier
                            )
                    )
                }
            }
        }
    }
}

private fun randomPattern(): Boolean = Math.random() < 0.25

/** Turns ASCII art (as produced by the picker) into a [Piece]. */
fun artToPiece(art: String): Piece = Piece.of("Manual", *art.split("\n").toTypedArray())

/** Renders a small piece for the tray slots and the picker. */
@Composable
fun MiniPiece(art: String, cellPx: Int = 14) {
    val rows = art.split("\n")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row {
                row.forEach { ch ->
                    Box(
                        Modifier.size(cellPx.dp)
                            .background(if (ch == '#') Color(0xFF7E9CC6) else Color.Transparent)
                    )
                }
            }
        }
    }
}

/** The scrollable list of shapes the user can pick from. */
@Composable
private fun PiecePicker(onPick: (String) -> Unit) {
    Column {
        PieceLibrary.all.forEach { piece ->
            val art = piece.render()
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clickable { onPick(art) }
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniPiece(art, cellPx = 11)
                    Spacer(Modifier.size(12.dp))
                    Text(piece.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}