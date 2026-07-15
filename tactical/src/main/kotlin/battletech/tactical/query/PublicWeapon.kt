package battletech.tactical.query

import battletech.tactical.unit.WeaponMountId
import kotlinx.serialization.Serializable

/**
 * The public projection of a [battletech.tactical.unit.Weapon]: name and mount
 * identity, nothing else. Both are observable/public — loadouts are published
 * in the Technical Readouts, and [mountId] is just plumbing that identifies
 * *which* mount a critical-hit slot refers to (see
 * [battletech.tui.view.GameLogFormatter]'s critical-hit line), not a hidden
 * fact about the unit.
 */
@Serializable
public data class PublicWeapon(val name: String, val mountId: WeaponMountId? = null)
