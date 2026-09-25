package io.archinaut.battletech.tactical.attack.physical

import io.archinaut.battletech.tactical.unit.CombatUnit
import kotlin.math.ceil

/** Punch damage (`docs/rules/physical-attacks.md` §5). */
public fun punchDamage(actor: CombatUnit): Int = ceil(actor.tonnage / 10.0).toInt()

/** Kick damage (`docs/rules/physical-attacks.md` §5). */
public fun kickDamage(actor: CombatUnit): Int = ceil(actor.tonnage / 5.0).toInt()
