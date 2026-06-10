package battletech.tactical.heat

/**
 * The universal BattleTech heat scale (0–30). Every penalty is keyed off a
 * unit's **absolute** heat level — heat-sink capacity affects dissipation only,
 * never penalties. Modifiers are non-cumulative: each query returns the single
 * worst applicable threshold. See `docs/rules/heat.md`.
 */
public object HeatScale {

    /** Movement-point penalty: −1 at 5, −2 at 10, −3 at 15, −4 at 20, −5 at 25. */
    public fun movementPenalty(heat: Int): Int = (heat / 5).coerceIn(0, 5)

    /** To-hit penalty applied to the overheating unit's own attacks. */
    public fun toHitPenalty(heat: Int): Int = when {
        heat >= 24 -> 4
        heat >= 18 -> 3
        heat >= 13 -> 2
        heat >= 8 -> 1
        else -> 0
    }

    /** 2D6 target to *avoid* shutdown, or null when no shutdown roll is required. */
    public fun shutdownAvoidTarget(heat: Int): Int? = when {
        heat >= 26 -> 10
        heat >= 22 -> 8
        heat >= 17 -> 6
        heat >= 14 -> 4
        else -> null
    }

    /** At 30+ the unit shuts down automatically, no roll. */
    public fun isAutoShutdown(heat: Int): Boolean = heat >= 30

    /** 2D6 target to *avoid* an ammo explosion, or null when no roll is required. */
    public fun ammoExplosionAvoidTarget(heat: Int): Int? = when {
        heat >= 28 -> 10
        heat >= 23 -> 8
        heat >= 19 -> 6
        heat >= 15 -> 4
        else -> null
    }
}
