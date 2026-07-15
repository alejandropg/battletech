package battletech.tactical.unit

import kotlinx.serialization.Serializable

@Serializable
public data class WeaponModel(
    public val name: String,
    public val damage: Int,
    public val heat: Int,
    public val minimumRange: Int = 0,
    public val shortRange: Int,
    public val mediumRange: Int,
    public val longRange: Int,
    public val criticalSlots: Int = 1,
    public val kind: WeaponKind,
    /**
     * True when this weapon can be fired while the mounting unit is fully submerged
     * (water depth ≥ 2). All standard weapon models are surface-only (`false`);
     * underwater-capable weapons are rare scenario-supplement items that must explicitly
     * opt in.
     *
     * When `false` and the attacker is at water depth ≥ 2, the
     * [battletech.tactical.attack.weapon.SubmergedWeaponRule] blocks the attack.
     */
    public val underwaterCapable: Boolean = false,
) {
    init {
        require(kind !is WeaponKind.Missile || damage == kind.clusterSize * kind.damagePerMissile) {
            "$name: damage ($damage) must equal clusterSize × damagePerMissile for a Missile kind"
        }
    }
}
