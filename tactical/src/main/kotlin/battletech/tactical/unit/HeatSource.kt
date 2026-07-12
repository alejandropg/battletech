package battletech.tactical.unit

import kotlinx.serialization.Serializable

/**
 * One labelled heat contribution accumulated during a turn (e.g. `Walking +1`,
 * `Medium Laser +3`). Sources collect on the acting unit as movement and
 * weapon fire are committed, and are summed into `currentHeat` during the Heat
 * Phase. The label drives the breakdown shown in the UNIT STATUS panel.
 */
@Serializable
public data class HeatSource(
    public val label: String,
    public val amount: Int,
)
