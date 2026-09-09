package dev.phosphor.mobil3.settings.appearance

/** Presentation-only protection. No result is written back into an authored appearance. */
object AppearancePresentationPolicy {
    fun foreground(argb: Int, background: Int, minimum: Double = 4.5): Int {
        val composite = AppearanceContrast.over(argb, background)
        if (AppearanceContrast.ratio(composite, background) >= minimum) return composite
        return if (AppearanceContrast.ratio(0, background) >= AppearanceContrast.ratio(0xffffff, background)) 0 else 0xffffff
    }

    fun spacing(baseDp: Float, densityScale: Float): Float = baseDp * densityScale
    fun target(requestedDp: Float): Float = requestedDp.coerceAtLeast(48f)

    enum class Post { HIDDEN, STATIC, REVEAL }

    fun post(activityVisible: Boolean, surfaceVisible: Boolean, componentVisible: Boolean,
        reduced: Boolean, motion: AppearanceMotion, retired: Boolean): Post = when {
        retired || !activityVisible || !surfaceVisible || !componentVisible -> Post.HIDDEN
        AppearanceMotionPolicy.stateChange(activityVisible, surfaceVisible, componentVisible, reduced, motion) -> Post.REVEAL
        else -> Post.STATIC
    }

    fun sectionAngle(expanded: Boolean): Float = if (expanded) 180f else 0f
    fun overflowIndex(active: Boolean): Float = if (active) 1f else 0f

    /** The caller's cancellable composition effect owns the supplied delay. */
    suspend fun revealPost(policy: Post, lines: (Int) -> Unit, retire: () -> Unit, pause: suspend (Long) -> Unit) {
        if (policy != Post.REVEAL) return
        lines(0)
        repeat(3) {
            pause(160)
            lines(it + 1)
        }
        pause(900)
        retire()
    }
}
