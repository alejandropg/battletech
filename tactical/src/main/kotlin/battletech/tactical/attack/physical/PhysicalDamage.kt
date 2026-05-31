package battletech.tactical.attack.physical

import battletech.tactical.unit.CombatUnit
import kotlin.math.ceil

/** Total Warfare: a punch deals ceil(tonnage / 10) damage. */
public fun punchDamage(actor: CombatUnit): Int = ceil(actor.tonnage / 10.0).toInt()

/** Total Warfare: a kick deals ceil(tonnage / 5) damage. */
public fun kickDamage(actor: CombatUnit): Int = ceil(actor.tonnage / 5.0).toInt()
