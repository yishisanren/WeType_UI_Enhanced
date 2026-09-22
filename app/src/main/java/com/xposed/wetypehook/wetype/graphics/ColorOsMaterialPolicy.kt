package com.xposed.wetypehook.wetype.graphics

/** Module policy, not undocumented ColorOS material tokens. */
internal object ColorOsMaterialPolicy {
    fun isColorOs(oplusVersion: String, oppoVersion: String): Boolean =
        listOf(oplusVersion, oppoVersion).any { it.trim().let { value -> value.isNotEmpty() && value != "0" } }

    fun blurRadius(value: Int): Int = value.coerceIn(0, 100)

    // Composite the user's tint over an opaque neutral surface when blur is unavailable.
    // Merely raising alpha would turn a transparent black tint into a black light keyboard.
    fun opaqueTint(color: Int, isDark: Boolean): Int {
        val base = if (isDark) 0xFF1C1D20.toInt() else 0xFFF4F5F7.toInt()
        val alpha = color ushr 24
        fun channel(shift: Int): Int =
            (((color ushr shift and 255) * alpha + (base ushr shift and 255) * (255 - alpha) + 127) / 255) shl shift
        return 0xFF000000.toInt() or channel(16) or channel(8) or channel(0)
    }
}

/** Strict binding of the AOSP/OPlus blur drawable contract; no optional glass calls. */
internal class ColorOsBlurHandle(private val target: Any, val drawable: Any) {
    private val radiusMethod = target.javaClass.getMethod("setBlurRadius", Int::class.javaPrimitiveType)
    private val colorMethod = target.javaClass.getMethod("setColor", Int::class.javaPrimitiveType)
    private val cornersMethod = target.javaClass.getMethod(
        "setCornerRadius", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
        Float::class.javaPrimitiveType, Float::class.javaPrimitiveType
    )

    fun configure(radius: Int, corners: WeTypeCornerRadii) {
        // AOSP order differs from GradientDrawable's clockwise array: TL, TR, BL, BR.
        // Enable blur last, so failed geometry/color configuration cannot leave an active layer.
        cornersMethod.invoke(target, corners.topLeft, corners.topRight, corners.bottomLeft, corners.bottomRight)
        colorMethod.invoke(target, 0)
        radiusMethod.invoke(target, ColorOsMaterialPolicy.blurRadius(radius))
    }

    fun clear() { radiusMethod.invoke(target, 0) }
}

/** One native layer per attached root; failure advances to the next backend only once. */
internal class ColorOsBlurSession(
    private val factories: List<Pair<String, (Any) -> ColorOsBlurHandle?>>,
    private val hideDrawable: (Any) -> Unit
) {
    private var root: Any? = null
    private var handle: ColorOsBlurHandle? = null
    private var nextFactory = 0
    var backend = "opaque-fallback"
        private set

    fun update(viewRoot: Any?, enabled: Boolean, radius: Int, corners: WeTypeCornerRadii): ColorOsBlurHandle? {
        if (root !== viewRoot) {
            clear()
            root = viewRoot
        }
        if (viewRoot == null || !enabled || radius <= 0) {
            releaseHandle()
            nextFactory = 0
            return null
        }
        while (true) {
            if (handle == null) {
                if (nextFactory >= factories.size) return null
                val (name, factory) = factories[nextFactory++]
                handle = runCatching { factory(viewRoot) }.getOrNull()
                if (handle == null) continue
                backend = name
            }
            if (runCatching { checkNotNull(handle).configure(radius, corners) }.isSuccess) return handle
            releaseHandle()
        }
    }

    private fun releaseHandle() {
        handle?.let {
            runCatching { it.clear() }
            runCatching { hideDrawable(it.drawable) }
        }
        handle = null
        backend = "opaque-fallback"
    }

    fun clear() {
        releaseHandle()
        root = null
        nextFactory = 0
    }
}
