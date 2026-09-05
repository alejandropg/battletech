package battletech.tui.animation

import battletech.tactical.attack.AttackResult
import battletech.tactical.unit.WeaponKind
import battletech.tactical.unit.WeaponModels

/**
 * The three weapon classes the overlay has an animation for, one apiece. Deliberately this app's
 * own enum rather than [WeaponKind] itself: the animations key off the weapon *class* only, while
 * [WeaponKind] also encodes the resolution mechanism (single-hit vs. Cluster Hits Table) and
 * carries ammo/cluster payloads that mean nothing to a picture.
 */
internal enum class WeaponCategory {
    ENERGY,
    BALLISTIC,
    MISSILE,
}

/**
 * The category [kind] belongs to. Exhaustive over [WeaponKind]'s sealed hierarchy on purpose — a
 * future weapon class breaks the build here rather than silently rendering nothing.
 */
internal fun categoryOf(kind: WeaponKind): WeaponCategory = when (kind) {
    is WeaponKind.Energy -> WeaponCategory.ENERGY
    is WeaponKind.Ballistic -> WeaponCategory.BALLISTIC
    is WeaponKind.Missile -> WeaponCategory.MISSILE
}

/**
 * The distinct categories fired in one resolved volley, in [WeaponCategory] declaration order.
 *
 * A resolved attack names its weapon only by display name, so each result is resolved through
 * [WeaponModels.findByName] — see that function for why the per-unit lookup can't be used here.
 * A miss counts exactly like a hit: the weapon was still fired, which is what the animation is
 * about. An unrecognised weapon name (a custom mech file, a model added later) contributes no
 * category rather than failing, so an entirely unrecognised volley simply plays nothing.
 */
internal fun categoriesOf(results: List<AttackResult>): List<WeaponCategory> =
    results
        .mapNotNull { WeaponModels.findByName(it.weaponName) }
        .map { categoryOf(it.kind) }
        .distinct()
        .sorted()
