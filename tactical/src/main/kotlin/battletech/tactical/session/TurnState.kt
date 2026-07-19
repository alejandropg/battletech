package battletech.tactical.session

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.attack.physical.PhysicalAttackDeclaration
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.UnitRoster
import battletech.tactical.unit.VisibleUnit
import kotlinx.serialization.Serializable

/**
 * Read surface shared by [MovementProgress] and [AttackProgress]: both drive an underlying
 * [sequence] and expose the same three derived views of it. Default-implemented so the two
 * implementers only need to supply [sequence] itself.
 */
public interface ImpulseProgress {
    public val sequence: ImpulseSequence
    public val currentImpulse: Impulse get() = sequence.current
    public val activePlayer: PlayerId get() = sequence.activePlayer
    public val isComplete: Boolean get() = sequence.isComplete
}

/** Movement-phase progress: who has moved, impulse position. All updates go through [afterUnitMoved]. */
@Serializable
public data class MovementProgress(
    override val sequence: ImpulseSequence = ImpulseSequence(emptyList()),
    val movedUnitIds: Set<UnitId> = emptySet(),
    val movedInCurrentImpulse: Int = 0,
) : ImpulseProgress {
    public val remainingInImpulse: Int get() = currentImpulse.unitCount - movedInCurrentImpulse

    /** Records the move and advances the impulse when its unit quota is reached. */
    public fun afterUnitMoved(unitId: UnitId): MovementProgress {
        val updated = copy(
            movedUnitIds = movedUnitIds + unitId,
            movedInCurrentImpulse = movedInCurrentImpulse + 1,
        )
        return if (updated.remainingInImpulse == 0) {
            updated.copy(
                sequence = updated.sequence.advance(),
                movedInCurrentImpulse = 0,
            )
        } else {
            updated
        }
    }
}

/**
 * Attack-phase progress. ONE shared [sequence] serves both attack phases:
 * the weapon phase completes it, then the physical phase's onEntry re-seeds it.
 */
@Serializable
public data class AttackProgress(
    override val sequence: ImpulseSequence = ImpulseSequence(emptyList()),
    val weaponDeclarations: List<AttackDeclaration> = emptyList(),
    val physicalDeclarations: List<PhysicalAttackDeclaration> = emptyList(),
) : ImpulseProgress {
    public val inProgress: Boolean get() = sequence.order.isNotEmpty() && !sequence.isComplete

    public fun seed(impulses: List<Impulse>): AttackProgress = copy(sequence = ImpulseSequence(impulses))

    /**
     * Records weapon declarations for the current impulse and advances the sequence.
     * When the sequence completes, the caller resolves the accumulated declarations
     * and then calls [clearWeaponDeclarations].
     */
    public fun recordWeaponImpulse(
        newDeclarations: List<AttackDeclaration>,
    ): AttackProgress = copy(
        weaponDeclarations = weaponDeclarations + newDeclarations,
        sequence = sequence.advance(),
    )

    /** Clears weapon declarations after they have been resolved. */
    public fun clearWeaponDeclarations(): AttackProgress = copy(weaponDeclarations = emptyList())

    /**
     * Records physical declarations for the current impulse and advances the sequence.
     */
    public fun recordPhysicalImpulse(
        newDeclarations: List<PhysicalAttackDeclaration>,
    ): AttackProgress = copy(
        physicalDeclarations = physicalDeclarations + newDeclarations,
        sequence = sequence.advance(),
    )

    /** Clears physical declarations after they have been resolved. */
    public fun clearPhysicalDeclarations(): AttackProgress = copy(physicalDeclarations = emptyList())
}

@Serializable
public data class TurnState(
    val initiative: Initiative,
    val movement: MovementProgress = MovementProgress(),
    val attack: AttackProgress = AttackProgress(),
    val turnNumber: Int = 1,
) {
    /**
     * Units still selectable to move this impulse: active for [MovementProgress.activePlayer]
     * and not yet moved. Generic over [UnitRoster]'s element type so it serves both the
     * authoritative [battletech.tactical.model.GameState] and, for deliveries (the TUI) that
     * hold only a per-viewer [battletech.tactical.query.PlayerGameState], its projected roster.
     * Only meaningful when [units] was projected for [MovementProgress.activePlayer] as viewer
     * — the caller's own units at that point — which every current call site guarantees
     * (selection/cycling only runs once the local turn guard has confirmed the viewer is the
     * active player).
     */
    public fun <T : VisibleUnit> selectableUnits(units: UnitRoster<T>): List<T> =
        units.activeOf(movement.activePlayer).all.filter { it.id !in movement.movedUnitIds }

    /** Counterpart of [selectableUnits] for the attack phase; see its KDoc. */
    public fun <T : VisibleUnit> selectableAttackUnits(units: UnitRoster<T>): List<T> =
        units.activeOf(attack.activePlayer).all

    public companion object {
        public val NULL: TurnState = TurnState(
            Initiative(emptyMap(), PlayerId.PLAYER_1, PlayerId.PLAYER_2),
        )
    }
}
