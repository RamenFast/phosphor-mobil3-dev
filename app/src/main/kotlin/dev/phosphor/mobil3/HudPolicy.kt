package dev.phosphor.mobil3

internal object HudPolicy {
    const val ENABLED = "floating_hud_enabled"
    const val BACKGROUND = "floating_hud_background"
    const val WIDTH = "floating_hud_width_dp"
    const val HEIGHT = "floating_hud_height_dp"
    enum class Background { SOLID, TRANSPARENT }
    data class Preferences(
        val enabled: Boolean = false,
        val background: Background = Background.SOLID,
        val width: Int = 320,
        val height: Int = 320,
    )
    fun read(values: Map<String, *>): Preferences = Preferences(
        values[ENABLED] as? Boolean ?: false,
        Background.entries.firstOrNull { it.name == values[BACKGROUND] } ?: Background.SOLID,
        (values[WIDTH] as? Int ?: 320).coerceIn(240, 640),
        (values[HEIGHT] as? Int ?: 320).coerceIn(240, 640),
    )
    fun refusal(explicit: Boolean, visible: Boolean, access: Boolean, locked: Boolean, microphone: Boolean): String? = when {
        !explicit || !visible -> "Show the HUD from the visible app"
        microphone -> "Microphone HUD needs a service-owned input. Keep this microphone in the app until that support is available"
        locked -> "Unlock the display before showing the HUD"
        !access -> "Allow display over other apps, then tap SHOW FLOATING HUD"
        else -> null
    }
    data class Bounds(val x: Int, val y: Int, val width: Int, val height: Int)
    fun presentationAllowed(visible: Boolean, interactive: Boolean, unlocked: Boolean, access: Boolean): Boolean =
        visible && interactive && unlocked && access
    fun bounds(x: Int, y: Int, width: Int, height: Int, availableWidth: Int, availableHeight: Int, minimum: Int): Bounds {
        val aw = availableWidth.coerceAtLeast(1)
        val ah = availableHeight.coerceAtLeast(1)
        val w = width.coerceIn(minimum.coerceIn(1, aw), aw)
        val h = height.coerceIn(minimum.coerceIn(1, ah), ah)
        return Bounds(x.coerceIn(0, aw - w), y.coerceIn(0, ah - h), w, h)
    }
}

/** A retired service must finish destruction before another explicit Show can use it. */
internal class HudSession {
    enum class Stage { IDLE, REQUESTED, VISIBLE, CLOSING }
    var stage = Stage.IDLE
        private set
    var generation = 0L
        private set
    val active get() = stage == Stage.REQUESTED || stage == Stage.VISIBLE
    fun request(): Long? {
        if (stage != Stage.IDLE) return null
        stage = Stage.REQUESTED
        return ++generation
    }
    fun accepts(token: Long): Boolean = token == generation && active
    fun shown(token: Long): Boolean {
        if (!accepts(token)) return false
        stage = Stage.VISIBLE
        return true
    }
    fun closing(token: Long): Boolean {
        if (token != generation || stage == Stage.IDLE) return false
        stage = Stage.CLOSING
        return true
    }
    fun destroyed(token: Long) {
        if (token == generation) stage = Stage.IDLE
    }
}

internal class HudCleanup {
    val failures = mutableListOf<String>()
    fun run(name: String, action: () -> Unit) {
        runCatching(action).onFailure { failures += name }
    }
}

/** Owns pending and completed connections through the same supported release API. */
internal class HudConnection<F : Any>(private val release: (F) -> Unit) {
    private var future: F? = null
    private var closed = false
    fun retain(value: F) {
        check(future == null && !closed)
        future = value
    }
    fun accepts(value: F): Boolean = !closed && future === value
    fun close() {
        if (closed) return
        closed = true
        val owned = future
        future = null
        owned?.let(release)
    }
}
