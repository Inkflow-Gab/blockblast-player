package com.example.blockblastplayer.vision

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the user's [Calibration] in shared preferences.
 *
 * The calibration screen can change any of these, so the app must remember
 * them between runs - the alternative (re-measuring from scratch every time)
 * would make the app useless on a phone with a slightly different layout.
 */
class CalibrationStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("blockblast_calibration", Context.MODE_PRIVATE)

    fun load(): Calibration = Calibration(
        panel = if (prefs.contains(KEY_L)) {
            Rect(
                prefs.getInt(KEY_L, 0),
                prefs.getInt(KEY_T, 0),
                prefs.getInt(KEY_R, -1),
                prefs.getInt(KEY_B, -1)
            ).takeIf { it.right > it.left && it.bottom > it.top }
        } else null,
        emptyColor = prefs.getInt(KEY_EMPTY, Rgb(DEFAULT_EMPTY_R, DEFAULT_EMPTY_G, DEFAULT_EMPTY_B).toInt()).let(Rgb::fromInt),
        filledThreshold = prefs.getInt(KEY_THRESHOLD, Calibration.DEFAULT_FILLED_THRESHOLD),
        gapColor = Rgb.fromInt(prefs.getInt(KEY_GAP, Rgb(DEFAULT_GAP_R, DEFAULT_GAP_G, DEFAULT_GAP_B).toInt())),
        gapTolerance = prefs.getInt(KEY_GAP_TOL, Calibration.DEFAULT_GAP_TOLERANCE),
        backgroundColor = Rgb.fromInt(prefs.getInt(KEY_BG, Rgb(DEFAULT_BG_R, DEFAULT_BG_G, DEFAULT_BG_B).toInt())),
        trayThreshold = prefs.getInt(KEY_TRAY_THRESHOLD, Calibration.DEFAULT_TRAY_THRESHOLD),
        trayBlockSize = prefs.getInt(KEY_TRAY_BLOCK, 0)
    )

    fun save(calibration: Calibration) {
        prefs.edit().apply {
            calibration.panel?.let {
                putInt(KEY_L, it.left); putInt(KEY_T, it.top)
                putInt(KEY_R, it.right); putInt(KEY_B, it.bottom)
            }
            putInt(KEY_EMPTY, calibration.emptyColor.toInt())
            putInt(KEY_THRESHOLD, calibration.filledThreshold)
            putInt(KEY_GAP, calibration.gapColor.toInt())
            putInt(KEY_GAP_TOL, calibration.gapTolerance)
            putInt(KEY_BG, calibration.backgroundColor.toInt())
            putInt(KEY_TRAY_THRESHOLD, calibration.trayThreshold)
            putInt(KEY_TRAY_BLOCK, calibration.trayBlockSize)
        }.apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_L = "panel_left"
        const val KEY_T = "panel_top"
        const val KEY_R = "panel_right"
        const val KEY_B = "panel_bottom"
        const val KEY_EMPTY = "empty_color"
        const val KEY_THRESHOLD = "filled_threshold"
        const val KEY_GAP = "gap_color"
        const val KEY_GAP_TOL = "gap_tol"
        const val KEY_BG = "bg_color"
        const val KEY_TRAY_THRESHOLD = "tray_threshold"
        const val KEY_TRAY_BLOCK = "tray_block"

        // defaults, matching Calibration.Companion
        const val DEFAULT_EMPTY_R = 38; const val DEFAULT_EMPTY_G = 46; const val DEFAULT_EMPTY_B = 85
        const val DEFAULT_GAP_R = 25; const val DEFAULT_GAP_G = 33; const val DEFAULT_GAP_B = 69
        const val DEFAULT_BG_R = 58; const val DEFAULT_BG_G = 82; const val DEFAULT_BG_B = 146
    }
}