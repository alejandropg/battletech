package battletech.tactical.unit

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
public value class UnitId(public val value: String)
