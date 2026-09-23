package com.xposed.wetypehook.wetype.graphics

import org.junit.Assert.*
import org.junit.Test

class ColorOsNativeStyleTest {
    @Test fun intensityCannotEscapeTheSupportedRange() {
        assertEquals(0f, ColorOsNativeStyle.strength(-1), 0f)
        assertEquals(1f, ColorOsNativeStyle.strength(100), 0f)
        assertEquals(2f, ColorOsNativeStyle.strength(Int.MAX_VALUE), 0f)
    }

    @Test fun usesNormalizedRgbaRatherThanArgbOrByteChannels() {
        assertArrayEquals(floatArrayOf(1f, 0f, 128f / 255f, 128f / 255f * 0.12f),
            ColorOsNativeStyle.rgba(0x80FF0080.toInt(), 0.12f), 0.00001f)
        assertEquals(0f, ColorOsNativeStyle.rgba(0x00FFFFFF, 1f)[3], 0f)
    }

    @Test fun keepsHardwareBottomCornersSeparateFromCustomTopCorners() {
        assertEquals(20f to 60f, ColorOsNativeStyle.radii(WeTypeCornerRadii(20f, 20f, 60f, 60f)))
        assertEquals(0f to 0f, ColorOsNativeStyle.radii(WeTypeCornerRadii.uniform(0f)))
    }

    @Test fun rejectsUnrepresentableOrInvalidGeometry() {
        assertNull(ColorOsNativeStyle.radii(WeTypeCornerRadii(10f, 20f, 30f, 30f)))
        assertNull(ColorOsNativeStyle.radii(WeTypeCornerRadii(10f, 10f, 30f, 40f)))
        assertNull(ColorOsNativeStyle.radii(WeTypeCornerRadii.uniform(Float.NaN)))
        assertNull(ColorOsNativeStyle.radii(WeTypeCornerRadii.uniform(Float.POSITIVE_INFINITY)))
        assertNull(ColorOsNativeStyle.radii(WeTypeCornerRadii.uniform(-1f)))
    }
}
