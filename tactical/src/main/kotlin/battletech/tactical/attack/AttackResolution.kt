package battletech.tactical.attack

import battletech.tactical.attack.physical.AttackDirection
import battletech.tactical.attack.physical.attackDirection
import battletech.tactical.dice.DiceRoll
import battletech.tactical.dice.DiceRoller
import battletech.tactical.heat.HeatScale
import battletech.tactical.model.GameState
import battletech.tactical.model.MechLocation
import battletech.tactical.session.CriticalHit
import battletech.tactical.session.GameEvent
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.Weapon
import battletech.tactical.unit.WeaponKind
import battletech.tactical.unit.consumeOneRoundFromAvailableBin

/**
 * Resolves [declarations] against [gameState] and applies the resulting damage and
 * critical hits, also returning the [CriticalHit] events produced, in volley order,
 * for callers (the weapon-attack phase handler) that need to surface them alongside
 * [battletech.tactical.session.AttacksResolved].
 *
 * Canonical dice order (must match seeded test expectations):
 *
 *  1. Per declaration, in order: to-hit 2d6, then (if hit) hit-location 2d6 — both in
 *     [resolveOneAttack]. All attacks roll against the *original* state (simultaneous
 *     resolution), so these rolls happen before any damage/crit dice are consumed.
 *  2. Then, in the same declaration order, damage is applied and any crit checks for
 *     that hit are rolled (2d6 crit table, then 1d6/1d6 slot picks with roll-again) on
 *     the *evolving* unit — so a later attack's roll-again sees an earlier attack's
 *     already-destroyed slots. A crit check fires once per [LocationDamage] step that
 *     dealt structure damage, plus once more for the hit location on a natural-2
 *     through-armor crit (`docs/rules/armor-damage.md` §3) even when no IS damage
 *     occurred. `isDestroyed`/`MatchEnded` evaluation is deferred to the session's
 *     post-volley destruction sweep, not decided here.
 */
public fun resolveAttacksWithCrits(
    declarations: List<AttackDeclaration>,
    gameState: GameState,
    roller: DiceRoller,
): Triple<GameState, List<AttackResult>, List<GameEvent>> {
    // All attacks resolve against the original state (simultaneous resolution)
    val results = declarations.map { declaration ->
        resolveOneAttack(declaration, gameState, roller)
    }

    // Apply all damage to get the final state, rolling crit checks in volley order.
    // For cluster weapons each group in locationHits is processed in order; for single-location
    // weapons locationHits contains exactly one element (same outcome as the old scalar path).
    // Partial cover: leg-location groups are skipped (no damage/crit) but location rolls are
    // already committed in pass-1, so the dice stream is unchanged for seeded tests.
    var updatedState = gameState
    val critEvents = mutableListOf<GameEvent>()
    val finalResults = results.map { result ->
        if (result is AttackResult.Hit) {
            var updatedTarget: CombatUnit = updatedState.unitById(result.targetId)
            val allDamageSteps = mutableListOf<LocationDamage>()

            for (locHit in result.locationHits) {
                val isLegLocation = locHit.location == HitLocation.LEFT_LEG ||
                    locHit.location == HitLocation.RIGHT_LEG
                if (result.partialCover && isLegLocation) continue

                val resolution = resolveDamage(updatedTarget, locHit.location, locHit.damage, result.useRearArmor)
                updatedTarget = resolution.unit
                allDamageSteps.addAll(resolution.steps)

                // Head-hit pilot damage: any IS penetration to HEAD → 1 pilot hit.
                // Canonical dice order: consciousness check 2d6 fires here, before
                // the crit-check dice for the same location hit.
                if (resolution.steps.any { it.location == MechLocation.HEAD && it.structureDamage >= 1 }) {
                    val (afterPilotHit, pilotHitEvents) = applyPilotHit(updatedTarget, roller)
                    updatedTarget = afterPilotHit
                    critEvents += pilotHitEvents
                }

                val naturalTwo = locHit.locationRoll.d1 == 1 && locHit.locationRoll.d2 == 1
                val critLocations = resolution.steps
                    .filter { it.structureDamage >= 1 }
                    .map { it.location }
                    .toMutableList()
                if (naturalTwo && locHit.location !in critLocations) {
                    critLocations += locHit.location
                }

                for (critLocation in critLocations) {
                    val (afterCrit, events) = resolveCriticalHits(updatedTarget, critLocation, roller)
                    updatedTarget = afterCrit
                    critEvents += events
                }
            }

            updatedState = updatedState.copy(
                units = updatedState.units.map { if (it.id == result.targetId) updatedTarget else it },
            )
            result.withDamage(allDamageSteps)
        } else {
            result
        }
    }

    // Apply ammo consumption: for each fired declaration whose weapon has ammoType != null,
    // decrement one round from the first non-empty matching bin on the attacker.
    // Applied as a final pass over the fully-resolved state so attacker ammo decrements
    // compose correctly when the same unit fires multiple weapons in one volley or when
    // an attacker is also a damage target. Draws from updatedState each iteration so
    // a multi-weapon volley chains correctly. No dice consumed.
    for (declaration in declarations) {
        val attacker = updatedState.unitById(declaration.attackerId)
        val weapon = attacker.weapons[declaration.weaponIndex]
        val ammoType = weapon.ammoType ?: continue
        // Consume from an available bin (skips bins in IS=0 locations).
        val updatedAttacker = attacker.consumeOneRoundFromAvailableBin(ammoType)
        updatedState = updatedState.copy(
            units = updatedState.units.map { if (it.id == attacker.id) updatedAttacker else it },
        )
    }

    return Triple(updatedState, finalResults, critEvents)
}

public fun applyDamage(
    unit: CombatUnit,
    location: HitLocation,
    damage: Int,
    useRearArmor: Boolean = false,
): CombatUnit = resolveDamage(unit, location, damage, useRearArmor).unit

/**
 * Applies [damage] to [location] on [unit]: armor first, then internal structure (IS).
 * If the location's IS is destroyed (reaches 0), any excess damage transfers inward
 * per the blow-through rules (`docs/rules/armor-damage.md` §5), hitting the new
 * location's armor first and preserving [useRearArmor]. Head and Center Torso do not
 * transfer; excess damage there is dropped.
 */
public fun resolveDamage(
    unit: CombatUnit,
    location: HitLocation,
    damage: Int,
    useRearArmor: Boolean = false,
): DamageResolution {
    if (damage <= 0) return DamageResolution(unit, emptyList())

    val currentArmor = unit.armor.at(location, useRearArmor)
    val armorAfter = (currentArmor - damage).coerceAtLeast(0)
    val armorDamage = minOf(currentArmor, damage)
    val toStructure = (damage - currentArmor).coerceAtLeast(0)
    val newArmor = unit.armor.with(location, armorAfter, useRearArmor)

    if (toStructure == 0) {
        val updatedUnit = unit.copy(armor = newArmor)
        val step = LocationDamage(
            location = location,
            armorDamage = armorDamage,
            structureDamage = 0,
            destroyed = false,
        )
        return DamageResolution(updatedUnit, listOf(step))
    }

    val currentIS = unit.internalStructure.at(location)
    val isAfter = (currentIS - toStructure).coerceAtLeast(0)
    val structureDamage = minOf(currentIS, toStructure)
    val destroyed = isAfter == 0
    val excess = (toStructure - currentIS).coerceAtLeast(0)
    val newIS = unit.internalStructure.with(location, isAfter)
    val updatedUnit = unit.copy(armor = newArmor, internalStructure = newIS)

    val step = LocationDamage(
        location = location,
        armorDamage = armorDamage,
        structureDamage = structureDamage,
        destroyed = destroyed,
    )

    val nextLocation = transferTarget(location)
    return if (excess > 0 && nextLocation != null) {
        val inner = resolveDamage(updatedUnit, nextLocation, excess, useRearArmor)
        DamageResolution(inner.unit, listOf(step) + inner.steps)
    } else {
        DamageResolution(updatedUnit, listOf(step))
    }
}

private fun transferTarget(location: HitLocation): HitLocation? = when (location) {
    HitLocation.LEFT_ARM, HitLocation.LEFT_LEG -> HitLocation.LEFT_TORSO
    HitLocation.RIGHT_ARM, HitLocation.RIGHT_LEG -> HitLocation.RIGHT_TORSO
    HitLocation.LEFT_TORSO, HitLocation.RIGHT_TORSO -> HitLocation.CENTER_TORSO
    HitLocation.HEAD, HitLocation.CENTER_TORSO -> null
}

private fun resolveOneAttack(
    declaration: AttackDeclaration,
    gameState: GameState,
    roller: DiceRoller,
): AttackResult {
    val attacker = gameState.unitById(declaration.attackerId)
    val target = gameState.unitById(declaration.targetId)
    val weapon = attacker.weapons[declaration.weaponIndex]

    val distance = attacker.position.distanceTo(target.position)
    val direction = attackDirection(attacker, target)
    val useRearArmor = direction == AttackDirection.REAR
    // target is passed as-is: the shared to-hit math only reads public fields off it (see
    // AttackContext) — the same input a client's projected snapshot would supply, whether the
    // resolver's target is a CombatUnit or (over the wire) a ForeignUnit.
    val modifiers = weaponToHitModifiers(attacker, target, weapon, distance, declaration.isPrimary, gameState.map)
    val los = lineOfSight(attacker.position, target.position, gameState.map)
    val rangeBand = rangeBandFor(distance, weapon)
    val targetNumber = weaponTargetNumber(attacker, modifiers)

    // Canonical dice order:
    //   1. to-hit 2d6
    //   2. (if hit, non-cluster) location 2d6
    //   2. (if hit, cluster) cluster-count 2d6, then one location 2d6 per group (in group order)
    val toHitRoll = roller.roll2d6()

    return if (toHitRoll.total >= targetNumber) {
        when (val kind = weapon.kind) {
            is WeaponKind.Missile ->
                clusterHit(declaration, attacker, weapon, kind, direction, useRearArmor, targetNumber, toHitRoll, rangeBand, modifiers, los.partialCover, roller)
            else ->
                singleHit(declaration, attacker, weapon, direction, useRearArmor, targetNumber, toHitRoll, rangeBand, modifiers, los.partialCover, roller)
        }
    } else {
        missResult(declaration, attacker, weapon, targetNumber, toHitRoll, rangeBand, modifiers, los.partialCover)
    }
}

private fun missResult(
    declaration: AttackDeclaration,
    attacker: CombatUnit,
    weapon: Weapon,
    targetNumber: Int,
    toHitRoll: DiceRoll,
    rangeBand: RangeBand,
    modifiers: List<ToHitModifier>,
    partialCover: Boolean,
): AttackResult.Miss = AttackResult.Miss(
    attackerId = declaration.attackerId,
    targetId = declaration.targetId,
    weaponName = weapon.name,
    targetNumber = targetNumber,
    toHitRoll = toHitRoll,
    gunnery = attacker.gunnerySkill,
    rangeBand = rangeBand,
    modifiers = modifiers,
    partialCover = partialCover,
)

/** Single-location weapon: one location roll. */
private fun singleHit(
    declaration: AttackDeclaration,
    attacker: CombatUnit,
    weapon: Weapon,
    direction: AttackDirection,
    useRearArmor: Boolean,
    targetNumber: Int,
    toHitRoll: DiceRoll,
    rangeBand: RangeBand,
    modifiers: List<ToHitModifier>,
    partialCover: Boolean,
    roller: DiceRoller,
): AttackResult.SingleHit {
    val locationRoll = roller.roll2d6()
    val hitLocation = HitLocationTable.roll(locationRoll.total, direction)
    return AttackResult.SingleHit(
        attackerId = declaration.attackerId,
        targetId = declaration.targetId,
        weaponName = weapon.name,
        targetNumber = targetNumber,
        toHitRoll = toHitRoll,
        gunnery = attacker.gunnerySkill,
        rangeBand = rangeBand,
        modifiers = modifiers,
        partialCover = partialCover,
        useRearArmor = useRearArmor,
        locationHits = listOf(LocationHit(hitLocation, weapon.damage, locationRoll)),
    )
}

/** Cluster weapon (SRM/LRM): roll cluster table → missiles hit → groups → per-group location roll. */
private fun clusterHit(
    declaration: AttackDeclaration,
    attacker: CombatUnit,
    weapon: Weapon,
    kind: WeaponKind.Missile,
    direction: AttackDirection,
    useRearArmor: Boolean,
    targetNumber: Int,
    toHitRoll: DiceRoll,
    rangeBand: RangeBand,
    modifiers: List<ToHitModifier>,
    partialCover: Boolean,
    roller: DiceRoller,
): AttackResult.ClusterHit {
    val clusterRoll = roller.roll2d6()
    val missiles = ClusterHitsTable.missilesHit(kind.clusterSize, clusterRoll.total)
    val groupDamages = buildClusterGroups(missiles, kind.missilesPerGroup, kind.damagePerMissile)
    val locationHits = groupDamages.map { groupDmg ->
        val locRoll = roller.roll2d6()
        LocationHit(HitLocationTable.roll(locRoll.total, direction), groupDmg, locRoll)
    }
    return AttackResult.ClusterHit(
        attackerId = declaration.attackerId,
        targetId = declaration.targetId,
        weaponName = weapon.name,
        targetNumber = targetNumber,
        toHitRoll = toHitRoll,
        gunnery = attacker.gunnerySkill,
        rangeBand = rangeBand,
        modifiers = modifiers,
        partialCover = partialCover,
        useRearArmor = useRearArmor,
        locationHits = locationHits,
        missilesHit = missiles,
    )
}

/**
 * Splits [missilesHit] into damage values per group.
 *
 * Full groups of [missilesPerGroup] contribute `missilesPerGroup × damagePerMissile` each.
 * A final partial group of `remainder` missiles contributes `remainder × damagePerMissile`.
 */
private fun buildClusterGroups(
    missilesHit: Int,
    missilesPerGroup: Int,
    damagePerMissile: Int,
): List<Int> {
    val fullGroups = missilesHit / missilesPerGroup
    val remainder = missilesHit % missilesPerGroup
    val groups = ArrayList<Int>(fullGroups + if (remainder > 0) 1 else 0)
    repeat(fullGroups) { groups.add(missilesPerGroup * damagePerMissile) }
    if (remainder > 0) groups.add(remainder * damagePerMissile)
    return groups
}

public fun heatPenaltyModifier(actor: CombatUnit): Int =
    HeatScale.toHitPenalty(actor.currentHeat)
