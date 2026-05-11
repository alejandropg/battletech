package battletech.tui.game

import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.AttackDeclaration

public class ImpulseDeclarations(
    public val playerId: PlayerId,
) {
    public val declarations: MutableMap<UnitId, UnitDeclaration> = mutableMapOf()

    public fun toAttackDeclarations(): List<AttackDeclaration> =
        declarations.values.flatMap { decl ->
            decl.weaponAssignments.flatMap { (targetId, weaponIndices) ->
                weaponIndices.map { weaponIndex ->
                    AttackDeclaration(
                        attackerId = decl.unitId,
                        targetId = targetId,
                        weaponIndex = weaponIndex,
                        isPrimary = targetId == decl.primaryTargetId,
                    )
                }
            }
        }
}
