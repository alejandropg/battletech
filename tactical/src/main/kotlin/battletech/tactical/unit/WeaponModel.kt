package battletech.tactical.unit

public data class WeaponModel(
    public val name: String,
    public val damage: Int,
    public val heat: Int,
    public val minimumRange: Int = 0,
    public val shortRange: Int,
    public val mediumRange: Int,
    public val longRange: Int,
    public val criticalSlots: Int = 1,
    public val ammoType: AmmoType? = null,
    /**
     * Number of missiles per salvo for cluster weapons (SRM/LRM); null for energy/ballistic
     * weapons. When non-null, resolution rolls the Cluster Hits Table then applies damage in
     * groups using [damagePerMissile] and [missilesPerGroup] instead of flat [damage].
     */
    public val clusterSize: Int? = null,
    /** Damage dealt per individual missile; 0 for non-cluster weapons. */
    public val damagePerMissile: Int = 0,
    /**
     * Number of missiles that share a single hit-location roll:
     * 1 for SRM (each missile rolls its own location), 5 for LRM (5-missile groups).
     */
    public val missilesPerGroup: Int = 1,
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
)
