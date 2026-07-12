package battletech.tactical.unit

import kotlinx.serialization.Serializable

@Serializable
public data class HeatSink(
    public val type: HeatSinkType,
    public val units: Int
) {
    public fun dissipation(): Int = type.sinkRatio * units
}
