package com.example.blockblastplayer.ui.vision

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.blockblastplayer.R
import com.example.blockblastplayer.overlay.OverlayService
import com.example.blockblastplayer.ui.manual.BoardGrid
import com.example.blockblastplayer.ui.manual.MiniPiece

/**
 * The Live tab: explains the two permissions, asks for them in plain language,
 * and shows the last thing the pipeline read.
 *
 * All permission handling lives here rather than in the service, so the user
 * is never surprised by a dialog with no explanation.
 */
@Composable
fun VisionScreen(
    modifier: Modifier = Modifier,
    startScreenCapture: ((Int, Intent?) -> Unit) -> Unit,
    requestNotificationPermission: ((Boolean) -> Unit) -> Unit,
    openOverlaySettings: () -> Unit
) {
    val context = LocalContext.current
    // Re-read the overlay permission every time this screen appears: the user
    // just came back from Settings, so the value has probably changed.
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasOverlay by remember { mutableStateOf(hasOverlayPermission(context)) }
    var hasNotifications by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var lastRead by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlay = hasOverlayPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Live suggestion", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Reads your screen, works out the best three moves, and draws them " +
                "over the game. It never taps for you.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))

        // ---- permission 1: draw over other apps --------------------------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.perm_overlay_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.perm_overlay_body),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (hasOverlay) "Allowed" else "Not allowed yet",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = hasOverlay, onCheckedChange = { hasOverlay = it })
                }
                Spacer(Modifier.height(6.dp))
                Button(onClick = {
                    openOverlaySettings()
                    hasOverlay = true
                }) { Text("Open Settings") }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- permission 2: screen capture -------------------------------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.perm_capture_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.perm_capture_body),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = hasOverlay,
                    onClick = {
                        startScreenCapture { code, data ->
                            if (code != 0 || data == null) {
                                lastRead = "Screen capture was cancelled."
                            } else {
                                runCatching {
                                    context.startForegroundService(
                                        OverlayService.startIntent(context, code, data)
                                    )
                                    running = true
                                }.onFailure {
                                    lastRead = "Could not start the overlay: ${it.message}"
                                }
                            }
                        }
                    }
                ) { Text("Start screen capture") }
                if (!hasOverlay) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Allow \"draw over other apps\" first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- permission 3: notifications (API 33+) ----------------------
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.perm_notifications_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.perm_notifications_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (hasNotifications) "Allowed" else "Not allowed yet",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = hasNotifications,
                            onCheckedChange = {
                                requestNotificationPermission { granted ->
                                    hasNotifications = granted
                                }
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (running) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Overlay is running", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "A round blue bubble has appeared on your screen. Tap it " +
                            "to analyse the position right now, or tap it twice " +
                            "to switch auto-refresh on. Drag it anywhere.\n\n" +
                            "Numbered ghost blocks (1, 2, 3) show where each piece " +
                            "goes. You still place them yourself.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        context.stopService(Intent(context, OverlayService::class.java))
                        running = false
                    }) { Text("Stop overlay") }
                }
            }
        }

        lastRead?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        Text("What the app does with the frame", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        listOf(
            "1. Find the 8x8 board: flood fill the dark navy gaps between cells.",
            "2. Sample each cell centre and compare it with the empty-cell colour.",
            "3. Read the three tray pieces below the board.",
            "4. Run the solver over every order and every legal position.",
            "5. Draw the winner on the overlay and forget the frame."
        ).forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** `Settings.canDrawOverlays` wrapper. */
fun hasOverlayPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)