package com.example.blockblastplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.blockblastplayer.ui.docs.DocsScreen
import com.example.blockblastplayer.ui.manual.ManualModeScreen
import com.example.blockblastplayer.ui.vision.CalibrationScreen
import com.example.blockblastplayer.ui.vision.VisionScreen
import com.example.blockblastplayer.ui.theme.BlockBlastTheme

/**
 * The single activity. It owns the navigation bar and hosts the four screens;
 * the heavy lifting lives in `overlay/OverlayService` and `vision/`.
 */
class MainActivity : ComponentActivity() {

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            pendingCapture?.invoke(result.resultCode, result.data)
            pendingCapture = null
        }

    /**
     * Android 13 (API 33) added a runtime permission for notifications. Without
     * it the foreground-service notification is silently dropped and the
     * overlay looks like it is running with no way to stop it.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingNotifications?.invoke(granted)
            pendingNotifications = null
        }

    private var pendingCapture: ((Int, Intent?) -> Unit)? = null
    private var pendingNotifications: ((Boolean) -> Unit)? = null

    /** Asks for POST_NOTIFICATIONS on Android 13+, silently skips on older. */
    fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        val needed = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!needed) {
            onResult(true)
            return
        }
        pendingNotifications = onResult
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Starts the system screen-capture consent dialog. */
    fun requestScreenCapture(onResult: (Int, Intent?) -> Unit) {
        pendingCapture = onResult
        val intent = com.example.blockblastplayer.vision.ScreenCapture.requestIntent(this)
        screenCaptureLauncher.launch(intent)
    }

    /**
     * Both of these take a *callback*, so their type is ((A) -> B) -> Unit.
     * Keeping that shape identical on both sides means a plain function
     * reference just works - no adapter lambda, no inference games.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BlockBlastTheme {
                AppRoot(
                    startScreenCapture = ::requestScreenCapture,
                    requestNotificationPermission = ::requestNotificationPermission,
                    openOverlaySettings = ::openOverlaySettings
                )
            }
        }
    }

    private fun openOverlaySettings() {
        // SYSTEM_ALERT_WINDOW is not a runtime permission - the user has to
        // flip it in Settings, so we send them straight there.
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    MANUAL("Manual", Icons.Filled.Calculate),
    VISION("Live", Icons.Filled.Visibility),
    CALIBRATE("Calibrate", Icons.Filled.Settings),
    DOCS("How it works", Icons.Filled.MenuBook)
}

@Composable
private fun AppRoot(
    startScreenCapture: ((Int, Intent?) -> Unit) -> Unit,
    requestNotificationPermission: ((Boolean) -> Unit) -> Unit,
    openOverlaySettings: () -> Unit
) {
    var tab by remember { mutableStateOf(Tab.MANUAL) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) }
                    )
                }
            }
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.MANUAL -> ManualModeScreen(modifier)
            Tab.VISION -> VisionScreen(
                modifier = modifier,
                startScreenCapture = startScreenCapture,
                requestNotificationPermission = requestNotificationPermission,
                openOverlaySettings = openOverlaySettings
            )
            Tab.CALIBRATE -> CalibrationScreen(modifier)
            Tab.DOCS -> DocsScreen(modifier)
        }
    }
}