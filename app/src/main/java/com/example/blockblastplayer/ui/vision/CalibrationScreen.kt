package com.example.blockblastplayer.ui.vision

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.blockblastplayer.vision.BoardReader
import com.example.blockblastplayer.vision.CalibrationStore
import com.example.blockblastplayer.vision.GridDetector
import com.example.blockblastplayer.vision.Rgb
import com.example.blockblastplayer.vision.Rect
import com.example.blockblastplayer.vision.toRgbImage
import com.example.blockblastplayer.ui.manual.BoardGrid

/**
 * Calibration: fix the vision up by hand when the automatic detection is not
 * happy (different phone, different game version, a floating FPS badge...).
 *
 * The user can nudge the panel rectangle, re-measure the empty-cell colour by
 * pointing at a known-empty square, and move the filled/empty threshold.
 */
@Composable
fun CalibrationScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { CalibrationStore(context) }
    var calibration by remember { mutableStateOf(store.load()) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("Load a screenshot, or start the live overlay and capture a frame.") }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Calibration", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Everything here was auto-measured from real screenshots. You only " +
                "need this screen if detection is wrong on your phone.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val frame = com.example.blockblastplayer.vision.LastFrame.image
                preview = frame?.let {
                    android.graphics.Bitmap.createBitmap(it.pixels, it.width, it.height, android.graphics.Bitmap.Config.ARGB_8888)
                }
                if (preview == null) {
                    status = "No captured frame yet - use the Live tab first."
                } else {
                    val rect = calibration.panel ?: GridDetector.detectPanel(frame!!, calibration)
                    if (rect != null) {
                        calibration = calibration.withPanel(rect)
                        status = "Auto-detected panel: x ${rect.left}..${rect.right}, y ${rect.top}..${rect.bottom}."
                    } else {
                        status = "Could not auto-detect. Drag the sliders to set the panel by hand."
                    }
                }
            }) { Text("Auto-detect") }

            OutlinedButton(onClick = {
                calibration = calibration.copy(panel = null)
                status = "Panel released - the app will auto-detect again."
            }) { Text("Release") }
        }

        Spacer(Modifier.height(12.dp))
        preview?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "captured frame",
                modifier = Modifier.fillMaxWidth().height(260.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(8.dp))
            val read = BoardReader.read(bmp.toRgbImage(), calibration)
            Text("What the reader sees from this frame:", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            val filled = ArrayList<Boolean>(64)
            for (i in 0 until 64) filled.add(read.board.isFilled(i / 8, i % 8))
            BoardGrid(filled = filled)
            Spacer(Modifier.height(6.dp))
            Text(read.message, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        Text("Board panel", style = MaterialTheme.typography.titleSmall)
        calibration.panel?.let { p ->
            Text(
                "x ${p.left}..${p.right}  (${p.width} px wide)\n" +
                    "y ${p.top}..${p.bottom}  (${p.height} px tall)\n" +
                    "cell ≈ ${(p.width / 8f).toInt()} x ${(p.height / 8f).toInt()} px",
                style = MaterialTheme.typography.bodySmall
            )
        } ?: Text("Not pinned - auto-detect each time.", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(8.dp))
        // nudge sliders
        calibration.panel?.let { p ->
            NudgeSlider("left", p.left, 0, 200) { calibration = calibration.withPanel(p.copy(left = it)) }
            NudgeSlider("top", p.top, 0, 900) { calibration = calibration.withPanel(p.copy(top = it)) }
            NudgeSlider("right", p.right, 0, 720) { calibration = calibration.withPanel(p.copy(right = it)) }
            NudgeSlider("bottom", p.bottom, 0, 1600) { calibration = calibration.withPanel(p.copy(bottom = it)) }
        }

        Spacer(Modifier.height(16.dp))
        Text("Colours", style = MaterialTheme.typography.titleSmall)
        calibration.panel?.let { p ->
            OutlinedButton(onClick = {
                val bmp = preview ?: return@OutlinedButton
                val img = bmp.toRgbImage()
                val cx = p.left + p.width / 16
                val cy = p.top + p.height / 16
                calibration = calibration.copy(emptyColor = Rgb.fromInt(img.at(cx, cy)))
                status = "Empty-cell colour set to RGB(${calibration.emptyColor.r}, ${calibration.emptyColor.g}, ${calibration.emptyColor.b})."
            }) { Text("Sample top-left cell as empty") }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Filled vs empty threshold: ${calibration.filledThreshold}\n" +
                "(a cell is 'filled' when its colour is this far from the empty colour)",
            style = MaterialTheme.typography.bodySmall
        )
        Slider(
            value = calibration.filledThreshold.toFloat(),
            onValueChange = { calibration = calibration.copy(filledThreshold = it.toInt()) },
            valueRange = 20f..200f
        )

        Spacer(Modifier.height(8.dp))
        Text("Tray block size: ${if (calibration.trayBlockSize == 0) "auto" else calibration.trayBlockSize} px")
        Slider(
            value = calibration.trayBlockSize.toFloat(),
            onValueChange = { calibration = calibration.copy(trayBlockSize = it.toInt()) },
            valueRange = 0f..80f
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                store.save(calibration)
                status = "Saved. The overlay will use these settings."
            }) { Text("Save") }
            OutlinedButton(onClick = {
                calibration = com.example.blockblastplayer.vision.Calibration()
                store.reset()
                status = "Reset to the measured defaults."
            }) { Text("Reset to measured defaults") }
        }
        Spacer(Modifier.height(8.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NudgeSlider(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text("$label = $value", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value.toFloat().coerceIn(min.toFloat(), max.toFloat()),
            onValueChange = { onChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat()
        )
    }
}