package com.example.blockblastplayer.vision

/**
 * A plain RGB image: width, height and one packed `0xRRGGBB` int per pixel,
 * row-major.
 *
 * This exists so the vision code has **no Android dependency**. On the phone
 * we build one from a `Bitmap`; in the unit tests we build one from a JPEG
 * using `javax.imageio`. The exact same [BoardReader] then runs in both
 * places, which is what makes the screenshot tests meaningful.
 */
class RgbImage(val width: Int, val height: Int, val pixels: IntArray) {

    init {
        require(pixels.size == width * height) {
            "expected ${width * height} pixels for ${width}x$height, got ${pixels.size}"
        }
    }

    fun at(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0
        return pixels[y * width + x]
    }

    fun red(x: Int, y: Int) = (at(x, y) shr 16) and 0xFF
    fun green(x: Int, y: Int) = (at(x, y) shr 8) and 0xFF
    fun blue(x: Int, y: Int) = at(x, y) and 0xFF

    fun contains(x: Int, y: Int) = x in 0 until width && y in 0 until height

    /** Nearest-neighbour crop, used by the calibration preview. */
    fun crop(x0: Int, y0: Int, w: Int, h: Int): RgbImage {
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            out[y * w + x] = at(x0 + x, y0 + y)
        }
        return RgbImage(w, h, out)
    }
}

/** An RGB colour, used for the calibrated reference colours. */
data class Rgb(val r: Int, val g: Int, val b: Int) {

    fun toInt(): Int = (r shl 16) or (g shl 8) or b

    /**
     * Sum of the absolute per-channel differences ("Manhattan distance" in
     * RGB space). Chosen because it is cheap, has no square root, and the gap
     * between our two reference colours is huge (see [colorDistance]).
     */
    fun distanceTo(other: Rgb): Int =
        kotlin.math.abs(r - other.r) + kotlin.math.abs(g - other.g) + kotlin.math.abs(b - other.b)

    companion object {
        fun fromInt(value: Int): Rgb = Rgb((value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF)

        /** Colour distance between two packed `0xRRGGBB` ints. */
        fun distance(a: Int, b: Int): Int {
            val dr = kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
            val dg = kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
            val db = kotlin.math.abs((a and 0xFF) - (b and 0xFF))
            return dr + dg + db
        }
    }
}