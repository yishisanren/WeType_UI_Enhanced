package com.xposed.wetypehook.wetype.graphics

import android.graphics.Color
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.xposed.wetypehook.PropertyUtils

/** Our tuning, not an OPPO preset. Geometry and units come from the running View. */
internal object ColorOsNativeStyle {
    fun strength(intensity: Int): Float = intensity.coerceIn(0, 200) / 100f
    fun rgba(color: Int, alphaScale: Float): FloatArray = floatArrayOf(
        (color ushr 16 and 255) / 255f, (color ushr 8 and 255) / 255f,
        (color and 255) / 255f, ((color ushr 24) / 255f * alphaScale).coerceIn(0f, 1f)
    )

    // The native API has one radius per View. Opposite halves keep independent top/bottom radii.
    // Fall back for left/right asymmetry instead of silently changing the user's outline.
    fun radii(corners: WeTypeCornerRadii): Pair<Float, Float>? =
        if (corners.topLeft == corners.topRight && corners.bottomLeft == corners.bottomRight &&
            corners.toArray().all { it.isFinite() && it >= 0f })
            corners.topLeft to corners.bottomLeft else null
}

/** Optional OPlus RenderNode decoration. Owns only decorative children of the IME carrier. */
internal class ColorOsNativeMaterial(private val carrier: ViewGroup) {
    private val api by lazy { runCatching { Api() }.getOrNull() }
    private var surfaces: List<View> = emptyList()
    private var failed = false
    private var lastSize: Pair<Int, Int>? = null
    var failure: String? = null
        private set

    fun apply(corners: WeTypeCornerRadii, isDark: Boolean, intensity: Int, color: Int): Boolean {
        val radii = ColorOsNativeStyle.radii(corners)
        val strength = ColorOsNativeStyle.strength(intensity)
        val animationLevel = PropertyUtils["persist.sys.oplus.anim_level", "-1"]?.toIntOrNull() ?: -1
        if (failed || api == null || radii == null || strength == 0f || !carrier.isHardwareAccelerated || animationLevel > 2) {
            clear(resetFailure = false)
            return false
        }
        return runCatching {
            if (surfaces.isEmpty()) {
                val created = mutableListOf<View>()
                surfaces = created
                repeat(2) {
                    View(carrier.context).apply {
                        created.add(this)
                        isClickable = false
                        isFocusable = false
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        carrier.addView(this, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
                    }
                }
            }
            val density = carrier.resources.displayMetrics.density
            surfaces.forEachIndexed { index, surface ->
                surface.setBackgroundColor(Color.TRANSPARENT)
                checkNotNull(api).apply(surface, if (index == 0) radii.first else radii.second,
                    density, strength, isDark, color)
            }
            updateGeometry()
        }.getOrElse {
            failure = (it.cause ?: it).javaClass.simpleName
            failed = true
            clear(resetFailure = false)
            false
        }
    }

    fun updateGeometry(): Boolean {
        if (surfaces.isEmpty()) return !failed
        if (carrier.width <= 0 || carrier.height <= 0) return true
        val size = carrier.width to carrier.height
        if (lastSize == size && surfaces.all { it.width == size.first && it.height == size.second }) return true
        return runCatching {
            val split = carrier.height / 2
            surfaces.forEachIndexed { index, surface ->
                surface.measure(View.MeasureSpec.makeMeasureSpec(carrier.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(carrier.height, View.MeasureSpec.EXACTLY))
                surface.layout(0, 0, carrier.width, carrier.height)
                surface.clipBounds = Rect(0, if (index == 0) 0 else split,
                    carrier.width, if (index == 0) split else carrier.height)
                api?.updateGeometry(surface, carrier.width, carrier.height)
                surface.invalidate()
            }
            lastSize = size
            true
        }.getOrElse {
            failure = (it.cause ?: it).javaClass.simpleName
            failed = true
            clear(resetFailure = false)
            false
        }
    }

    fun clear(resetFailure: Boolean = true) {
        surfaces.forEach { surface ->
            runCatching { api?.clear(surface) }
            surface.visibility = View.INVISIBLE
            carrier.removeView(surface)
        }
        surfaces = emptyList()
        lastSize = null
        if (resetFailure) { failed = false; failure = null }
    }

    private class Api {
        private val loader = View::class.java.classLoader
        private fun type(name: String) = Class.forName("com.oplus.view.material.$name", false, loader)
        private val util = type("OplusMaterialUtil")
        private val corner = type("OplusMaterialCornerParams")
        private val edge = type("OplusMaterialEdgeParams")
        private val shadow = type("OplusMaterialShadowParams")
        private val base = type("OplusMaterialBaseParams")
        private val floatType = Float::class.javaPrimitiveType!!
        private val intType = Int::class.javaPrimitiveType!!
        private val cornerConstructor = corner.getConstructor(floatType, floatType)
        private val edgeConstructor = edge.getConstructor(intType, floatType, floatType, floatType)
        private val shadowConstructor = shadow.getConstructor(intType, floatType, floatType, floatType)
        private val baseConstructor = base.getConstructor(intType, Rect::class.java)
        private val setCorner = util.getMethod("setCornerParams", View::class.java, corner)
        private val setEdge = util.getMethod("setEdgeParams", View::class.java, edge)
        private val setShadow = util.getMethod("setShadowParams", View::class.java, shadow)
        private val setBase = util.getMethod("setBaseParams", View::class.java, base)
        private val edgeRectangle = edge.getField("EDGE_TYPE_RECTANGLE").getInt(null)
        private val shadowRectangle = shadow.getField("SHADOW_TYPE_RECTANGLE").getInt(null)
        private val smoothWeight = (Class.forName("com.oplus.view.OplusSmoothRoundedManager", false, loader)
            .getMethod("getDefaultG2Weight").invoke(null) as Number).toFloat()
        private val getWrapper = View::class.java.getMethod("getViewWrapper")
        private val getRenderNode = getWrapper.returnType.getMethod("getRenderNode")
        private val setBackdrop = getRenderNode.returnType.getMethod("setBackgroundRenderEffect", RenderEffect::class.java)
        private val configured = mutableSetOf<View>()

        fun apply(view: View, radius: Float, density: Float, strength: Float, dark: Boolean, color: Int) {
            // Native edges decorate a backdrop pass, as in COUI. Cross-window sampling remains
            // on the carrier's BackgroundBlurDrawable; this pass shades only our own surface.
            val changedBackdrop = setBackdrop.invoke(getRenderNode.invoke(getWrapper.invoke(view)), RenderEffect.createColorFilterEffect(
                BlendModeColorFilter(color, BlendMode.SRC_OVER),
                RenderEffect.createBlurEffect(1f, 1f, Shader.TileMode.CLAMP))) as Boolean
            // A fresh node must accept its first backdrop. A false result on an already
            // configured node can simply mean that the native value is unchanged.
            check(changedBackdrop || view in configured)
            configured.add(view)
            // These setters return whether a value changed, not whether rendering succeeded.
            check(smoothWeight.isFinite() && smoothWeight > 0f)
            setCorner.invoke(null, view, cornerConstructor.newInstance(radius, smoothWeight))
            setEdge.invoke(null, view, edgeConstructor.newInstance(edgeRectangle, 2f * density,
                ((if (dark) 0.45f else 0.55f) * strength).coerceAtMost(1f), 0f))
            setShadow.invoke(null, view, shadowConstructor.newInstance(shadowRectangle, 1f,
                (if (dark) 0.025f else 0.07f) * strength, 0f))
            view.invalidate()
        }

        fun updateGeometry(view: View, width: Int, height: Int) {
            setBase.invoke(null, view, baseConstructor.newInstance(0, Rect(0, 0, width, height)))
        }

        fun clear(view: View) {
            runCatching { setBackdrop.invoke(getRenderNode.invoke(getWrapper.invoke(view)), null) }
            configured.remove(view)
            runCatching { setBase.invoke(null, view, null) }
            // Attempt all resets even when one private method fails after a ROM change.
            runCatching { setEdge.invoke(null, view, null) }
            runCatching { setShadow.invoke(null, view, null) }
            runCatching { setCorner.invoke(null, view, null) }
            view.invalidate()
        }
    }
}
