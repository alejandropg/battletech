package battletech.tactical.unit

public data class HeatSink(
    public val type: HeatSinkType,
    public val units: Int
) {
    public fun dissipation(): Int = type.sinkRatio * units
}
