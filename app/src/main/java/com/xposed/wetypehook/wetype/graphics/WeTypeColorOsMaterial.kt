package com.xposed.wetypehook.wetype.graphics

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import com.xposed.wetypehook.xposed.Log

/** Experimental ColorOS 16/17 frosted surface. Does not claim native Condensed Light. */
internal class WeTypeColorOsMaterial(private val view: View) {
    private val session = ColorOsBlurSession(
        listOf("oplus" to { _: Any -> createOplusHandle() }, "aosp" to { root: Any -> createAospHandle(root) }),
        hideDrawable = { (it as Drawable).setVisible(false, false) }
    )
    private var reportedBackend: String? = null

    fun apply(color: Int, radius: Int, corners: WeTypeCornerRadii, isDark: Boolean,
              highlight: Boolean, intensity: Int) {
        val viewRoot = runCatching { View::class.java.getMethod("getViewRootImpl").invoke(view) }.getOrNull()
        val blurEnabled = WeTypeMaterialEnvironment.isBlurEnabled(view.context)
        val blurRadius = ColorOsMaterialPolicy.blurRadius(radius)
        val handle = session.update(viewRoot, blurEnabled, blurRadius, corners)
        val blurred = handle != null
        val surfaceColor = if (blurred || (blurEnabled && blurRadius == 0)) color
            else ColorOsMaterialPolicy.opaqueTint(color, isDark)
        val tint = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = corners.toArray()
            setColor(surfaceColor)
        }
        val layers = buildList {
            if (blurred) add(checkNotNull(handle).drawable as Drawable)
            add(tint)
            if (highlight) add(WeTypeBloomStrokeDrawable(view.context, corners, surfaceColor, intensity.coerceIn(0, 200) / 100f))
        }
        view.background = if (layers.size == 1) tint else LayerDrawable(layers.toTypedArray())
        val status = if (blurred) session.backend else if (blurEnabled && blurRadius == 0) "tint-only" else "opaque-fallback"
        if (reportedBackend != status) {
            Log.i("ColorOS material: $status; crossWindowBlur=$blurEnabled; device validation pending")
            reportedBackend = status
        }
    }

    private fun createOplusHandle(): ColorOsBlurHandle? = runCatching {
        // Optional ROM wrapper; these are probed signatures, not a published OPPO SDK.
        val type = Class.forName("com.oplus.view.ViewRootManager", false, View::class.java.classLoader)
        val manager = type.getConstructor(View::class.java).newInstance(view)
        val drawable = type.getMethod("getBackgroundBlurDrawable").invoke(manager) as Drawable
        bindHandle(manager, drawable)
    }.getOrNull()

    private fun createAospHandle(viewRoot: Any): ColorOsBlurHandle? = runCatching {
        val drawable = viewRoot.javaClass.getMethod("createBackgroundBlurDrawable").invoke(viewRoot) as Drawable
        bindHandle(drawable, drawable)
    }.getOrNull()

    private fun bindHandle(owner: Any, drawable: Drawable): ColorOsBlurHandle? = runCatching {
        ColorOsBlurHandle(owner, drawable)
    }.getOrElse {
        drawable.setVisible(false, false)
        null
    }

    fun clear() {
        session.clear()
        view.background = null
    }
}
