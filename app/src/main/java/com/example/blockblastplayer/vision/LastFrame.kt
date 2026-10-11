package com.example.blockblastplayer.vision

/**
 * The most recent captured frame, kept only so the calibration screen can
 * show the user *what the app actually sees*.
 *
 * It holds a single frame in memory - never written to disk, never sent
 * anywhere - and it is overwritten on every capture.
 */
object LastFrame {

    @Volatile
    var image: RgbImage? = null

    fun set(image: RgbImage?) {
        this.image = image
    }
}