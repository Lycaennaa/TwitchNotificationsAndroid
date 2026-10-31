package com.lycaennaa.twitchalerts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Icon
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object TwitchStatusBarIcon {
    private const val ICON_SIZE_DP = 24

    fun fromAvatar(context: Context, avatar: Bitmap): Icon? {
        val metrics = context.resources.displayMetrics
        val size = (ICON_SIZE_DP * metrics.density).roundToInt().coerceAtLeast(1)
        val cropSize = minOf(avatar.width, avatar.height)
        val cropLeft = (avatar.width - cropSize) / 2
        val cropTop = (avatar.height - cropSize) / 2
        val iconBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(iconBitmap).drawBitmap(
            avatar,
            Rect(cropLeft, cropTop, cropLeft + cropSize, cropTop + cropSize),
            Rect(0, 0, size, size),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )

        val pixels = IntArray(size * size)
        iconBitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        if (!applyAlphaMask(pixels, size)) return null
        iconBitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        iconBitmap.density = metrics.densityDpi
        return Icon.createWithBitmap(iconBitmap)
    }

    internal fun applyAlphaMask(pixels: IntArray, size: Int): Boolean {
        require(size > 0 && pixels.size == size * size)

        var minimumLuminance = 255
        var maximumLuminance = 0
        for (color in pixels) {
            if (color ushr 24 and 0xff == 0) continue
            val luminance = luminance(color)
            minimumLuminance = minOf(minimumLuminance, luminance)
            maximumLuminance = maxOf(maximumLuminance, luminance)
        }
        if (minimumLuminance == 255 && maximumLuminance == 0) {
            pixels.fill(0x00ffffff)
            return false
        }

        val luminanceRange = maximumLuminance - minimumLuminance
        val radius = size / 2f
        val center = size / 2f
        var hasVisiblePixel = false
        for (y in 0 until size) {
            val dy = y + 0.5f - center
            for (x in 0 until size) {
                val index = y * size + x
                val color = pixels[index]
                val sourceAlpha = color ushr 24 and 0xff
                val normalizedLuminance = if (luminanceRange == 0) {
                    128
                } else {
                    ((maximumLuminance - luminance(color)) * 255f / luminanceRange).roundToInt()
                }
                val dx = x + 0.5f - center
                val circleCoverage = (radius + 0.5f - sqrt(dx * dx + dy * dy)).coerceIn(0f, 1f)
                val alpha = (sourceAlpha * normalizedLuminance / 255f * circleCoverage).roundToInt()
                if (alpha > 0) hasVisiblePixel = true
                pixels[index] = (alpha shl 24) or 0x00ffffff
            }
        }
        return hasVisiblePixel
    }

    private fun luminance(color: Int): Int {
        val red = color ushr 16 and 0xff
        val green = color ushr 8 and 0xff
        val blue = color and 0xff
        return (red * 54 + green * 183 + blue * 19 + 128) shr 8
    }
}
