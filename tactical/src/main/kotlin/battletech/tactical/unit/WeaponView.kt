package battletech.tactical.unit

/**
 * The observable face of a weapon mount: name and mount identity, nothing else. [Weapon]
 * (the full record-sheet entry) and [PublicWeapon] (the redacted projection for a foreign
 * unit) both implement this and expose nothing beyond it, which is what lets
 * [VisibleUnit.weapons] be typed uniformly across own and foreign units.
 *
 * Never serialized directly — [VisibleUnit.weapons] is only ever a `List<Weapon>` or a
 * `List<PublicWeapon>` at the concrete subtype, each with its own `@Serializable` element
 * type, so this interface needs no serializer of its own.
 */
public interface WeaponView {
    public val name: String
    public val mountId: WeaponMountId?
}
