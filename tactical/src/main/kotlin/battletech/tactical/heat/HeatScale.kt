package battletech.tactical.heat

/**
 * The universal heat scale (`docs/rules/heat.md` §2–3). Each query returns the single
 * worst applicable threshold rather than a cumulative total.
 */
public object HeatScale {

    /** Movement-point penalty (`docs/rules/heat.md` §2). */
    public fun movementPenalty(heat: Int): Int = (heat / 5).coerceIn(0, 5)

    /** To-hit penalty applied to the overheating unit's own attacks (`docs/rules/heat.md` §2). */
    public fun toHitPenalty(heat: Int): Int = when {
        heat >= 24 -> 4
        heat >= 18 -> 3
        heat >= 13 -> 2
        heat >= 8 -> 1
        else -> 0
    }

    /** 2d6 target to *avoid* shutdown, or null when no roll is required (`docs/rules/heat.md` §2). */
    public fun shutdownAvoidTarget(heat: Int): Int? = when {
        heat >= 26 -> 10
        heat >= 22 -> 8
        heat >= 17 -> 6
        heat >= 14 -> 4
        else -> null
    }

    /** Whether the unit shuts down automatically, with no roll (`docs/rules/heat.md` §2). */
    public fun isAutoShutdown(heat: Int): Boolean = heat >= 30

    /** 2d6 target to *avoid* an ammo explosion, or null when no roll is required (`docs/rules/heat.md` §2). */
    public fun ammoExplosionAvoidTarget(heat: Int): Int? = when {
        heat >= 28 -> 10
        heat >= 23 -> 8
        heat >= 19 -> 6
        heat >= 15 -> 4
        else -> null
    }
}
