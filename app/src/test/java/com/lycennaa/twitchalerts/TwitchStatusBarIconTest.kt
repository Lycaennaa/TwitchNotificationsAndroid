package com.lycaennaa.twitchalerts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class TwitchStatusBarIconTest {
    @Test
    fun alphaMaskKeepsMonochromePhotoContrastInsideCircularCrop() {
        val pixels = IntArray(25) { 0xffffffff.toInt() }
        pixels[12] = 0xff000000.toInt()
        pixels[13] = 0xff808080.toInt()

        TwitchStatusBarIcon.applyAlphaMask(pixels, 5)

        assertEquals(0, pixels[0] ushr 24)
        assertEquals(255, pixels[12] ushr 24)
        assertEquals(127, pixels[13] ushr 24)
        assertEquals(0x00ffffff, pixels[12] and 0x00ffffff)
    }
    @Test
    fun uniformAvatarStillProducesVisibleStatusBarIcon() {
        val pixels = IntArray(25) { 0xffffffff.toInt() }

        TwitchStatusBarIcon.applyAlphaMask(pixels, 5)

        assertEquals(128, pixels[12] ushr 24)
        assertEquals(22, pixels[0] ushr 24)
    }
    @Test
    fun transparentAvatarLeavesNoStatusBarMask() {
        val pixels = IntArray(25)

        assertFalse(TwitchStatusBarIcon.applyAlphaMask(pixels, 5))
        assertEquals(0, pixels[12] ushr 24)
    }
}
