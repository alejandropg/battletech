package battletech.tactical.unit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public sealed interface CriticalSlotContent {
    @Serializable
    public data object Empty : CriticalSlotContent
    @Serializable
    public data object Engine : CriticalSlotContent
    @Serializable
    public data object Gyro : CriticalSlotContent
    @Serializable
    public data object Sensors : CriticalSlotContent
    @Serializable
    public data object LifeSupport : CriticalSlotContent
    @Serializable
    public data object Cockpit : CriticalSlotContent
    @Serializable
    public data class Actuator(@SerialName("actuatorType") public val type: ActuatorType) : CriticalSlotContent
    @Serializable
    public data class WeaponMount(public val weaponId: WeaponMountId) : CriticalSlotContent
    @Serializable
    public data class AmmoBin(
        @SerialName("ammoType") public val type: AmmoType,
        public val shots: Int,
    ) : CriticalSlotContent
    @Serializable
    public data object HeatSink : CriticalSlotContent
    @Serializable
    public data object JumpJet : CriticalSlotContent
}
