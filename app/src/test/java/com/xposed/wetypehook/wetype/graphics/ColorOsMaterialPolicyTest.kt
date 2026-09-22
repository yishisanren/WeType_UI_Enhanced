package com.xposed.wetypehook.wetype.graphics

import org.junit.Assert.*
import org.junit.Test

class ColorOsMaterialPolicyTest {
    @Test fun identifiesRomPropertiesWithoutTreatingMissingPropertiesAsColorOs() {
        assertTrue(ColorOsMaterialPolicy.isColorOs("V17.0", ""))
        assertTrue(ColorOsMaterialPolicy.isColorOs("", "V16.0"))
        assertFalse(ColorOsMaterialPolicy.isColorOs("", ""))
        assertFalse(ColorOsMaterialPolicy.isColorOs("0", " "))
    }

    @Test fun transparentTintFallsBackToReadableOpaqueSurfaceInBothModes() {
        assertEquals(0xFFF4F5F7.toInt(), ColorOsMaterialPolicy.opaqueTint(0, false))
        assertEquals(0xFF1C1D20.toInt(), ColorOsMaterialPolicy.opaqueTint(0, true))
    }

    @Test fun opaqueUserColorsArePreserved() {
        for (color in listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF338877.toInt())) {
            assertEquals(color, ColorOsMaterialPolicy.opaqueTint(color, false))
            assertEquals(color, ColorOsMaterialPolicy.opaqueTint(color, true))
        }
    }

    @Test fun translucentTintIsCompositedRatherThanForciblyMadeBlack() {
        assertEquals(0xFF7A7A7B.toInt(), ColorOsMaterialPolicy.opaqueTint(0x80000000.toInt(), false))
        assertEquals(255, ColorOsMaterialPolicy.opaqueTint(0x40000000, true) ushr 24)
    }

    @Test fun nativeCornersUseBottomLeftBeforeBottomRight() {
        val target = FakeBlur()
        val handle = ColorOsBlurHandle(target, target)
        handle.configure(60, WeTypeCornerRadii(10f, 20f, 30f, 40f))
        assertEquals(listOf("corners:10.0,20.0,40.0,30.0", "color:0", "radius:60"), target.calls)
    }

    @Test fun clampsCorruptPreferencesAndDisablesTheNativeLayerOnClear() {
        val target = FakeBlur()
        val handle = ColorOsBlurHandle(target, target)
        handle.configure(Int.MAX_VALUE, WeTypeCornerRadii.uniform(0f))
        assertEquals("radius:100", target.calls.last())
        handle.configure(-1, WeTypeCornerRadii.uniform(0f))
        assertEquals("radius:0", target.calls.last())
        handle.configure(60, WeTypeCornerRadii.uniform(0f))
        handle.clear()
        assertEquals("radius:0", target.calls.last())
    }

    @Test fun rejectsIncompleteRomContractsBeforeEnablingBlur() {
        assertThrows(NoSuchMethodException::class.java) { ColorOsBlurHandle(Any(), Any()) }
    }

    @Test fun geometryFailureNeverEnablesBlur() {
        val target = FailingBlur()
        val handle = ColorOsBlurHandle(target, target)
        assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
            handle.configure(60, WeTypeCornerRadii.uniform(10f))
        }
        assertTrue(target.calls.isEmpty())
        handle.clear()
        assertEquals(listOf("radius:0"), target.calls)
    }

    open class FakeBlur {
        val calls = mutableListOf<String>()
        fun setBlurRadius(radius: Int) { calls.add("radius:$radius") }
        fun setColor(color: Int) { calls.add("color:$color") }
        open fun setCornerRadius(tl: Float, tr: Float, bl: Float, br: Float) {
            calls.add("corners:$tl,$tr,$bl,$br")
        }
    }
    class FailingBlur : FakeBlur() {
        override fun setCornerRadius(tl: Float, tr: Float, bl: Float, br: Float) {
            error("ROM signature exists but invocation is unsupported")
        }
    }
}
