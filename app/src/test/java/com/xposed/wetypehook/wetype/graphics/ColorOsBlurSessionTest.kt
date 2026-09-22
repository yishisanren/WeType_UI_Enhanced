package com.xposed.wetypehook.wetype.graphics

import org.junit.Assert.*
import org.junit.Test

class ColorOsBlurSessionTest {
    private val corners = WeTypeCornerRadii(10f, 20f, 30f, 40f)

    @Test fun reusesOneLayerAcrossStyleChangesAndReleasesItOnHide() {
        var creations = 0
        val hidden = mutableListOf<Any>()
        val target = ColorOsMaterialPolicyTest.FakeBlur()
        val session = ColorOsBlurSession(listOf("test" to { _: Any ->
            creations++; ColorOsBlurHandle(target, target)
        }), hidden::add)
        val root = Any()
        assertSame(session.update(root, true, 60, corners), session.update(root, true, 30, corners))
        assertEquals(1, creations)
        session.clear()
        assertEquals(listOf(target), hidden)
        assertEquals("radius:0", target.calls.last())
        session.clear()
        assertEquals(1, hidden.size)
    }

    @Test fun oldRootIsReleasedBeforeAllocatingANewLayer() {
        val order = mutableListOf<String>()
        val targets = mutableListOf<ColorOsMaterialPolicyTest.FakeBlur>()
        val session = ColorOsBlurSession(listOf("test" to { _: Any ->
            order.add("create")
            val target = ColorOsMaterialPolicyTest.FakeBlur().also(targets::add)
            ColorOsBlurHandle(target, target)
        }), { order.add("hide"); assertEquals("radius:0", (it as ColorOsMaterialPolicyTest.FakeBlur).calls.last()) })
        session.update(Any(), true, 60, corners)
        session.update(Any(), true, 60, corners)
        assertEquals(listOf("create", "hide", "create"), order)
        assertEquals(2, targets.size)
    }

    @Test fun disabledBlurReleasesAndReenabledBlurCreatesAFreshLayer() {
        var creations = 0
        var hides = 0
        val session = ColorOsBlurSession(listOf("test" to { _: Any ->
            creations++
            val target = ColorOsMaterialPolicyTest.FakeBlur()
            ColorOsBlurHandle(target, target)
        }), { hides++ })
        val root = Any()
        assertNotNull(session.update(root, true, 60, corners))
        assertNull(session.update(root, false, 60, corners))
        assertNull(session.update(root, false, 60, corners))
        assertEquals(1, hides)
        assertNotNull(session.update(root, true, 60, corners))
        assertEquals(2, creations)
        assertNull(session.update(root, true, 0, corners))
        assertEquals(2, hides)
    }

    @Test fun failedOemInvocationIsCleanedBeforeAospFallback() {
        val oem = ColorOsMaterialPolicyTest.FailingBlur()
        val aosp = ColorOsMaterialPolicyTest.FakeBlur()
        val hidden = mutableListOf<Any>()
        val session = ColorOsBlurSession(listOf(
            "oplus" to { _: Any -> ColorOsBlurHandle(oem, oem) },
            "aosp" to { _: Any -> ColorOsBlurHandle(aosp, aosp) }
        ), hidden::add)
        assertSame(aosp, session.update(Any(), true, 60, corners)?.drawable)
        assertEquals(listOf(oem), hidden)
        assertEquals("radius:0", oem.calls.last())
        assertEquals("aosp", session.backend)
    }

    @Test fun unavailableFactoriesDoNotRetryOnEveryStyleUpdate() {
        var attempts = 0
        val session = ColorOsBlurSession(listOf(
            "missing" to { _: Any -> attempts++; null },
            "throwing" to { _: Any -> attempts++; error("unavailable") }
        ), { fail("No drawable was allocated") })
        val root = Any()
        repeat(10) { assertNull(session.update(root, true, 60, corners)) }
        assertEquals(2, attempts)
        assertEquals("opaque-fallback", session.backend)
        session.clear()
        session.update(root, true, 60, corners)
        assertEquals(4, attempts)
    }

    @Test fun detachedRootDoesNotAllocateAndClearsAnExistingSurface() {
        var creations = 0
        var hides = 0
        val session = ColorOsBlurSession(listOf("test" to { _: Any ->
            creations++
            val target = ColorOsMaterialPolicyTest.FakeBlur()
            ColorOsBlurHandle(target, target)
        }), { hides++ })
        assertNull(session.update(null, true, 60, corners))
        assertEquals(0, creations)
        session.update(Any(), true, 60, corners)
        assertNull(session.update(null, true, 60, corners))
        assertEquals(1, hides)
    }
}
