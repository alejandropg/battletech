package battletech.tactical.query

import battletech.tactical.model.PlayerId
import battletech.tactical.unit.ArmorLayout
import battletech.tactical.unit.UnitId

public data class PublicWeapon(val name: String)

/**
 * A presentation projection, not a security boundary. The TUI's UNIT
 * STATUS / TARGET STATUS panels use [PublicUnit] to show a reduced,
 * glanceable summary for units the viewer doesn't own — see
 * `UnitStatusSubject.Public`, `TargetStatusView`, `PublicUnitPanel`,
 * `AttackPhase.targetStatusUnit`, and `SelectingCommon.cursorUnitStatus`.
 *
 * The full `CombatUnit` (armor diagram, ammo, critical hits, everything)
 * remains freely available for every unit via `PlayerView.state` and
 * `GameSession.gameState` regardless of ownership: the game is
 * open-information by design (see the KDoc on
 * `battletech.network.server.GameServer.snapshot`), so there is nothing to
 * hide here. This type exists purely to keep the status panels uncluttered,
 * not to withhold information from a client.
 */
public data class PublicUnit(
    val id: UnitId,
    val owner: PlayerId,
    val name: String,
    val walkingMP: Int,
    val runningMP: Int,
    val jumpMP: Int,
    val armor: ArmorLayout,
    val weapons: List<PublicWeapon>,
)
