package com.xposed.wetypehook.wetype.graphics

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.xposed.wetypehook.PropertyUtils
import java.util.function.Consumer

internal object WeTypeMaterialEnvironment {
    val isColorOs: Boolean by lazy {
        ColorOsMaterialPolicy.isColorOs(
            PropertyUtils["ro.build.version.oplusrom", ""].orEmpty(),
            PropertyUtils["ro.build.version.opporom", ""].orEmpty()
        )
    }

    fun isBlurEnabled(context: Context): Boolean = runCatching {
        context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
    }.getOrDefault(false)

    fun isAvailable(context: Context): Boolean =
        if (isColorOs) isBlurEnabled(context) else WeTypeHyperMaterial.isAvailable(context)

    fun observeAvailability(context: Context, onChanged: () -> Unit): () -> Unit {
        if (!isColorOs) return WeTypeHyperMaterial.observeAvailability(context, onChanged)
        val manager = context.getSystemService(WindowManager::class.java) ?: return {}
        val handler = Handler(Looper.getMainLooper())
        var active = true
        val listener = Consumer<Boolean> { handler.post { if (active) onChanged() } }
        val registered = runCatching { manager.addCrossWindowBlurEnabledListener(listener) }.isSuccess
        return {
            active = false
            if (registered) runCatching { manager.removeCrossWindowBlurEnabledListener(listener) }
            Unit
        }
    }
}
