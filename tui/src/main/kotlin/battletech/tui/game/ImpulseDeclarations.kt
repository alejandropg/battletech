package battletech.tui.game

import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.AttackDeclaration

public class ImpulseDeclarations(
    public val playerId: PlayerId,
    public val unitCount: Int,
) {
    public val declarations: MutableMap<UnitId, UnitDeclaration> = mutableMapOf()

    public fun isDeclared(unitId: UnitId): Boolean {
        val status = declarations[unitId]?.status ?: return false
        return status == DeclarationStatus.WEAPONS_ASSIGNED || status == DeclarationStatus.NO_ATTACK
    }

    public fun allDeclared(): Boolean =
        declarations.values.count { it.status != DeclarationStatus.PENDING } == unitCount

    public fun toAttackDeclarations(): List<AttackDeclaration> =
        declarations.values.flatMap { decl ->
            if (decl.status == DeclarationStatus.NO_ATTACK) return@flatMap emptyList()
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
