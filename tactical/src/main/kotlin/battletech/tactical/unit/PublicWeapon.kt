package battletech.tactical.unit

import kotlinx.serialization.Serializable

/**
 * The public projection of a [Weapon]: name and mount identity, nothing else. Both are
 * observable/public — loadouts are published in the Technical Readouts, and [mountId] is
 * just plumbing that identifies *which* mount a critical-hit slot refers to (see
 * [battletech.tui.view.GameLogFormatter]'s critical-hit line), not a hidden fact about the
 * unit.
 */
@Serializable
public data class PublicWeapon(override val name: String, override val mountId: WeaponMountId) : WeaponView
