package com.xposed.wetypehook.wetype.graphics

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import com.xposed.wetypehook.xposed.Log

/** ColorOS blur plus optional native material decoration. Does not claim optical refraction. */
internal class WeTypeColorOsMaterial(private val view: View) {
    private val nativeMaterial = (view as? ViewGroup)?.let { ColorOsNativeMaterial(it) }
    private val session = ColorOsBlurSession(
        listOf("oplus" to { _: Any -> createOplusHandle() }, "aosp" to { root: Any -> createAospHandle(root) }),
        hideDrawable = { (it as Drawable).setVisible(false, false) }
    )
    private var reportedBackend: String? = null
    private var nativeApplied = false
    private var reapply: (() -> Unit)? = null

    fun apply(color: Int, radius: Int, corners: WeTypeCornerRadii, isDark: Boolean,
              highlight: Boolean, intensity: Int) {
        reapply = { apply(color, radius, corners, isDark, highlight, intensity) }
        val viewRoot = runCatching { View::class.java.getMethod("getViewRootImpl").invoke(view) }.getOrNull()
        val blurEnabled = WeTypeMaterialEnvironment.isBlurEnabled(view.context)
        val blurRadius = ColorOsMaterialPolicy.blurRadius(radius)
        val handle = session.update(viewRoot, blurEnabled, blurRadius, corners)
        val blurred = handle != null
        val nativeMix = handle?.configureMaterial(color) == true
        val surfaceColor = if (blurred || (blurEnabled && blurRadius == 0)) color
            else ColorOsMaterialPolicy.opaqueTint(color, isDark)
        val tint = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = corners.toArray()
            setColor(surfaceColor)
        }
        val nativeStroke = if (blurred && highlight && intensity > 0)
            nativeMaterial?.apply(corners, isDark, intensity, surfaceColor) == true else {
                nativeMaterial?.clear()
                false
            }
        val layers = buildList {
            if (blurred) add(checkNotNull(handle).drawable as Drawable)
            if (!nativeStroke) add(tint)
            if (highlight && !nativeStroke) add(WeTypeBloomStrokeDrawable(view.context, corners, surfaceColor, intensity.coerceIn(0, 200) / 100f))
        }
        nativeApplied = nativeStroke
        view.background = if (layers.size == 1) layers.single() else LayerDrawable(layers.toTypedArray())
        val backend = if (blurred) session.backend else if (blurEnabled && blurRadius == 0) "tint-only" else "opaque-fallback"
        val status = "$backend; nativeMix=$nativeMix; nativeStroke=$nativeStroke"
        if (reportedBackend != status) {
            val message = "ColorOS material: $status; crossWindowBlur=$blurEnabled; failure=${nativeMaterial?.failure}"
            Log.i(message)
            android.util.Log.i("WeTypeColorOS", message)
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
        reapply = null
        nativeApplied = false
        nativeMaterial?.clear()
        session.clear()
        view.background = null
        if (reportedBackend != null) android.util.Log.i("WeTypeColorOS", "ColorOS material: cleared")
        reportedBackend = null
    }

    fun updateGeometry() {
        if (nativeApplied && nativeMaterial?.updateGeometry() == false) reapply?.invoke()
    }
}
