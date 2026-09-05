package battletech.tui.animation

/**
 * One panel of a volley, and how far through its animation it is.
 *
 * A panel is [Pending] while it waits out its staggered start, [Playing] for one pass of its
 * animation, and then gone. Each animation has its own length and frame rate, so panels appear and
 * vanish independently of one another.
 */
internal sealed interface PanelState {
    val animation: WeaponAnimation
    val placement: PanelPlacement

    /** This panel one step on, or `null` once its last frame has played and it should disappear. */
    fun next(): PanelState?

    data class Pending(
        override val animation: WeaponAnimation,
        override val placement: PanelPlacement,
    ) : PanelState {
        /** The first tick after the stagger elapses is what puts this panel on screen. */
        override fun next(): PanelState = Playing(animation, placement, frameIndex = 0)
    }

    data class Playing(
        override val animation: WeaponAnimation,
        override val placement: PanelPlacement,
        val frameIndex: Int,
    ) : PanelState {
        override fun next(): PanelState? =
            if (frameIndex + 1 >= animation.frameCount) null else copy(frameIndex = frameIndex + 1)
    }
}

/** A frame plus where it goes — the only animation type `battletech.tui.view.Workspace` sees. */
internal data class AnimationPanel(
    val animation: WeaponAnimation,
    val frameIndex: Int,
    val x: Int,
    val y: Int,
)

/**
 * Everything on screen for one resolved volley: one panel per distinct weapon category fired.
 *
 * [generation] identifies this volley so a tick left over from a previous, already-cancelled one is
 * recognised as stale — the same generation-stamping pattern `battletech.tui.loop.runLoop` uses for
 * [tenter.view.FlashMessage] expiry.
 *
 * [panels] is keyed by slot rather than held as a list because panels are removed individually as
 * they finish: list indices would shift under the ticks still in flight for the other slots. Slot
 * order is also paint order, so a panel can never flip above or below a neighbour mid-life.
 */
internal data class VolleyPlayback(
    val generation: Long,
    val panels: Map<Int, PanelState>,
) {
    /**
     * This volley with slot [slot] advanced one step: started if it was pending, moved on a frame
     * if it was playing, dropped once its animation ends. `null` once that leaves nothing on
     * screen — the whole volley is then over. An unknown [slot] is returned unchanged, since a
     * cancelled panel's job can post one last tick before it notices.
     */
    fun advance(slot: Int): VolleyPlayback? {
        val state = panels[slot] ?: return this
        val advanced = state.next()
        val remaining = if (advanced == null) panels - slot else panels + (slot to advanced)
        return if (remaining.isEmpty()) null else copy(panels = remaining)
    }

    /** The panels actually on screen, bottom-first — pending ones have not appeared yet. */
    fun visible(): List<AnimationPanel> = panels.entries
        .sortedBy { it.key }
        .mapNotNull { (_, state) ->
            (state as? PanelState.Playing)?.let {
                AnimationPanel(it.animation, it.frameIndex, it.placement.x, it.placement.y)
            }
        }

    internal companion object {
        /**
         * A volley playing one panel per entry of [animations] at the matching entry of
         * [placements]. Slot 0 starts [PanelState.Playing] immediately so its first frame lands in
         * the same render as the event that triggered it; every later slot starts
         * [PanelState.Pending] and is put on screen by its own staggered first tick.
         */
        fun start(
            animations: List<WeaponAnimation>,
            placements: List<PanelPlacement>,
            generation: Long,
        ): VolleyPlayback? {
            if (animations.isEmpty() || placements.size != animations.size) return null
            val panels = animations.mapIndexed { slot, animation ->
                val placement = placements[slot]
                slot to if (slot == 0) {
                    PanelState.Playing(animation, placement, frameIndex = 0)
                } else {
                    PanelState.Pending(animation, placement)
                }
            }.toMap()
            return VolleyPlayback(generation, panels)
        }
    }
}
