package com.example.blockblastplayer.vision

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.Inflater

/**
 * A small PNG reader, written for the unit tests only.
 *
 * ## Why this exists
 *
 * The obvious way to load a screenshot in a JVM test is `javax.imageio`, but
 * Android unit tests compile against `android.jar`, which **does not contain
 * `java.awt` or `javax.imageio`**. Importing `ImageIO` fails with
 * "Unresolved reference". That is a dead end, not a mistake.
 *
 * So the sample screenshots are stored as PNG (which is lossless - the exact
 * pixel values the vision code is calibrated against are preserved) and
 * decoded here with `java.util.zip.Inflater`, which *is* part of the Android
 * SDK.
 *
 * ## Supported
 *
 * 8-bit, non-interlaced, colour type 0 (grey), 2 (RGB) or 6 (RGBA) - which
 * covers everything the fixtures use. Anything else throws rather than
 * returning wrong pixels.
 *
 * This lives in `test/` and is never shipped in the APK.
 */
object PngReader {

    private val SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    fun read(stream: InputStream): RgbImage = read(stream.readBytes())

    fun read(bytes: ByteArray): RgbImage {
        for (i in SIGNATURE.indices) {
            require(bytes[i] == SIGNATURE[i]) { "not a PNG file" }
        }

        var pos = 8
        var width = 0
        var height = 0
        var bitDepth = 0
        var colourType = 0
        var interlace = 0
        val idat = ByteArrayOutputStream()

        while (pos + 8 <= bytes.size) {
            val length = readInt(bytes, pos)
            pos += 4
            val type = String(bytes, pos, 4, Charsets.US_ASCII)
            pos += 4
            if (pos + length > bytes.size) break
            val data = bytes.copyOfRange(pos, pos + length)
            pos += length + 4 // skip the CRC; a corrupted fixture is not worth handling

            when (type) {
                "IHDR" -> {
                    width = readInt(data, 0)
                    height = readInt(data, 4)
                    bitDepth = data[8].toInt() and 0xFF
                    colourType = data[9].toInt() and 0xFF
                    interlace = data[12].toInt() and 0xFF
                }
                "IDAT" -> idat.write(data)
                "IEND" -> break
            }
        }

        require(width > 0 && height > 0) { "PNG has no usable header" }
        require(bitDepth == 8) { "only 8-bit PNGs are supported, got $bitDepth" }
        require(interlace == 0) { "interlaced PNGs are not supported" }
        val channels = when (colourType) {
            0 -> 1
            2 -> 3
            6 -> 4
            else -> error("unsupported PNG colour type $colourType")
        }

        val inflated = inflate(idat.toByteArray())
        val stride = width * channels
        require(inflated.size >= (stride + 1) * height) { "PNG pixel data is truncated" }

        val pixels = IntArray(width * height)
        var previous = ByteArray(stride)
        var current = ByteArray(stride)
        var offset = 0

        for (y in 0 until height) {
            val filter = inflated[offset].toInt() and 0xFF
            offset++
            System.arraycopy(inflated, offset, current, 0, stride)
            offset += stride
            unfilter(current, previous, channels, filter)

            for (x in 0 until width) {
                val o = x * channels
                val r = current[o].toInt() and 0xFF
                val g = if (channels >= 3) current[o + 1].toInt() and 0xFF else r
                val b = if (channels >= 3) current[o + 2].toInt() and 0xFF else r
                pixels[y * width + x] = (r shl 16) or (g shl 8) or b
            }

            val swap = previous
            previous = current
            current = swap
        }

        return RgbImage(width, height, pixels)
    }

    /** Reverses one PNG scanline filter in place. */
    private fun unfilter(current: ByteArray, previous: ByteArray, bpp: Int, filter: Int) {
        val length = current.size
        when (filter) {
            0 -> Unit // None
            1 -> for (i in length - 1 downTo 0) {
                val a = if (i >= bpp) current[i - bpp].toInt() and 0xFF else 0
                current[i] = ((current[i].toInt() and 0xFF) + a).toByte()
            }
            2 -> for (i in 0 until length) {
                current[i] = ((current[i].toInt() and 0xFF) + (previous[i].toInt() and 0xFF)).toByte()
            }
            3 -> for (i in 0 until length) {
                val a = if (i >= bpp) current[i - bpp].toInt() and 0xFF else 0
                val b = previous[i].toInt() and 0xFF
                current[i] = ((current[i].toInt() and 0xFF) + ((a + b) / 2)).toByte()
            }
            4 -> for (i in 0 until length) {
                val a = if (i >= bpp) current[i - bpp].toInt() and 0xFF else 0
                val b = previous[i].toInt() and 0xFF
                val c = if (i >= bpp) previous[i - bpp].toInt() and 0xFF else 0
                current[i] = ((current[i].toInt() and 0xFF) + paeth(a, b, c)).toByte()
            }
            else -> error("unknown PNG filter $filter")
        }
    }

    /** The Paeth predictor from the PNG spec. */
    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = if (p > a) p - a else a - p
        val pb = if (p > b) p - b else b - p
        val pc = if (p > c) p - c else c - p
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream(data.size * 2)
        val buffer = ByteArray(1 shl 16)
        try {
            while (!inflater.finished()) {
                val produced = inflater.inflate(buffer)
                if (produced == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, produced)
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}