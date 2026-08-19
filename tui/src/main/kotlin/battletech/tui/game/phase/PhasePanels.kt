package battletech.tui.game.phase

import battletech.tactical.unit.ForeignUnit
import battletech.tui.game.GamePanelId

/**
 * The side panels the active phase contributes THIS frame, as content. Presence IS visibility:
 * [ids] is derived from which fields are set, so a panel can never be shown with nothing in it.
 *
 * The always-on and cross-phase panels (LOG, UNIT STATUS, ATTACK RESULTS, HELP) are decided by
 * [battletech.tui.game.PanelVisibility], not here — this type carries only a phase's own workflow
 * panels, and must not become a second panel registry.
 *
 * NOT a data class: [declaredTargets] holds a [Lazy], so generated equals/hashCode would compare
 * thunk identity. This is a per-frame carrier, never compared.
 */
internal class PhasePanels(
    /** TARGETS content. Forced — this panel's visibility depends on the target list being non-empty. */
    val targets: AttackRender? = null,
    /** TARGET STATUS content. Forced, and cheap: derived from the same query as [targets]. */
    val targetStatus: ForeignUnit? = null,
    /**
     * DECLARED TARGETS content, deferred. Visibility here is a property of the phase
     * (`attackTurnPhase == WEAPON_ATTACK`), not of the content, so presence stays cheap while the
     * value — a walk of every declaration plus to-hit math — is paid only if the panel's builder
     * actually runs (it may be MINIMIZED).
     */
    val declaredTargets: Lazy<DeclaredTargetsRender>? = null,
) {
    val ids: Set<GamePanelId> = buildSet {
        if (targets != null) add(GamePanelId.TARGETS)
        if (targetStatus != null) add(GamePanelId.TARGET_STATUS)
        if (declaredTargets != null) add(GamePanelId.DECLARED_TARGETS)
    }

    internal companion object {
        internal val NONE: PhasePanels = PhasePanels()
    }
}
