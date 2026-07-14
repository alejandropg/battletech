package battletech.tactical.attack

import battletech.tactical.attack.physical.AttackDirection
import battletech.tactical.attack.physical.attackDirection
import battletech.tactical.dice.DiceRoller
import battletech.tactical.heat.HeatScale
import battletech.tactical.model.GameState
import battletech.tactical.model.MechLocation
import battletech.tactical.session.CriticalHit
import battletech.tactical.session.GameEvent
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.consumeOneRoundFromAvailableBin

/**
 * Resolves [declarations] against [gameState] and applies the resulting damage and
 * critical hits. Canonical dice order (must match seeded test expectations):
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
public fun resolveAttacks(
    declarations: List<AttackDeclaration>,
    gameState: GameState,
    roller: DiceRoller,
): Pair<GameState, List<AttackResult>> {
    val (state, results, _) = resolveAttacksWithCrits(declarations, gameState, roller)
    return state to results
}

/**
 * Same resolution as [resolveAttacks] but also returns the [CriticalHit] events
 * produced, in volley order, for callers (the weapon-attack phase handler) that need
 * to surface them alongside [battletech.tactical.session.AttacksResolved].
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
        if (result.hit && result.locationHits.isNotEmpty()) {
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
            result.copy(damage = allDamageSteps)
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
    val modifiers = weaponToHitModifiers(attacker, target, weapon, distance, declaration.isPrimary, gameState)
    val los = lineOfSight(attacker, target, gameState.map)
    val rangeBand = rangeBandFor(distance, weapon)
    val rangeModifier = modifiers.amountOf(ToHitFactor.RANGE)
    val heatPenalty = modifiers.amountOf(ToHitFactor.HEAT)
    val secondaryPenalty = modifiers.amountOf(ToHitFactor.SECONDARY_TARGET)
    val sensorModifier = modifiers.amountOf(ToHitFactor.SENSORS)
    val attackerMoveModifier = modifiers.amountOf(ToHitFactor.ATTACKER_MOVEMENT)
    val targetMoveModifier = modifiers.amountOf(ToHitFactor.TARGET_MOVEMENT)
    val minRangeModifier = modifiers.amountOf(ToHitFactor.MINIMUM_RANGE)
    val targetNumber = weaponTargetNumber(attacker, modifiers)

    // Canonical dice order:
    //   1. to-hit 2d6
    //   2. (if hit, non-cluster) location 2d6
    //   2. (if hit, cluster) cluster-count 2d6, then one location 2d6 per group (in group order)
    val toHitRoll = roller.roll2d6()

    return if (toHitRoll.total >= targetNumber) {
        val clusterSize = weapon.clusterSize
        if (clusterSize != null) {
            // Cluster weapon: roll cluster table → missiles hit → groups → per-group location roll.
            val clusterRoll = roller.roll2d6()
            val missiles = ClusterHitsTable.missilesHit(clusterSize, clusterRoll.total)
            val groupDamages = buildClusterGroups(missiles, weapon.missilesPerGroup, weapon.damagePerMissile)
            val locationHits = groupDamages.map { groupDmg ->
                val locRoll = roller.roll2d6()
                LocationHit(HitLocationTable.roll(locRoll.total, direction), groupDmg, locRoll)
            }
            val firstHit = locationHits.first()
            AttackResult(
                attackerId = declaration.attackerId,
                targetId = declaration.targetId,
                weaponName = weapon.name,
                hit = true,
                hitLocation = firstHit.location,
                damageApplied = locationHits.sumOf { it.damage },
                targetNumber = targetNumber,
                roll = toHitRoll.total,
                toHitRoll = toHitRoll,
                locationRoll = firstHit.locationRoll,
                gunnery = attacker.gunnerySkill,
                rangeModifier = rangeModifier,
                rangeBand = rangeBand,
                heatPenalty = heatPenalty,
                secondaryPenalty = secondaryPenalty,
                sensorPenalty = sensorModifier,
                attackerMoveModifier = attackerMoveModifier,
                targetMoveModifier = targetMoveModifier,
                minRangeModifier = minRangeModifier,
                modifiers = modifiers,
                partialCover = los.partialCover,
                useRearArmor = useRearArmor,
                locationHits = locationHits,
                missilesHit = missiles,
            )
        } else {
            // Single-location weapon: one location roll, exactly as before.
            val locationRoll = roller.roll2d6()
            val hitLocation = HitLocationTable.roll(locationRoll.total, direction)
            AttackResult(
                attackerId = declaration.attackerId,
                targetId = declaration.targetId,
                weaponName = weapon.name,
                hit = true,
                hitLocation = hitLocation,
                damageApplied = weapon.damage,
                targetNumber = targetNumber,
                roll = toHitRoll.total,
                toHitRoll = toHitRoll,
                locationRoll = locationRoll,
                gunnery = attacker.gunnerySkill,
                rangeModifier = rangeModifier,
                rangeBand = rangeBand,
                heatPenalty = heatPenalty,
                secondaryPenalty = secondaryPenalty,
                sensorPenalty = sensorModifier,
                attackerMoveModifier = attackerMoveModifier,
                targetMoveModifier = targetMoveModifier,
                minRangeModifier = minRangeModifier,
                modifiers = modifiers,
                partialCover = los.partialCover,
                useRearArmor = useRearArmor,
                locationHits = listOf(LocationHit(hitLocation, weapon.damage, locationRoll)),
                missilesHit = null,
            )
        }
    } else {
        AttackResult(
            attackerId = declaration.attackerId,
            targetId = declaration.targetId,
            weaponName = weapon.name,
            hit = false,
            hitLocation = null,
            damageApplied = 0,
            targetNumber = targetNumber,
            roll = toHitRoll.total,
            toHitRoll = toHitRoll,
            locationRoll = null,
            gunnery = attacker.gunnerySkill,
            rangeModifier = rangeModifier,
            rangeBand = rangeBand,
            heatPenalty = heatPenalty,
            secondaryPenalty = secondaryPenalty,
            sensorPenalty = sensorModifier,
            attackerMoveModifier = attackerMoveModifier,
            targetMoveModifier = targetMoveModifier,
            minRangeModifier = minRangeModifier,
            modifiers = modifiers,
            partialCover = los.partialCover,
        )
    }
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
