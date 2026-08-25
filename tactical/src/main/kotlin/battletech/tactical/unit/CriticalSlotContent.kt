package battletech.tactical.unit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public sealed interface CriticalSlotContent {
    @Serializable
    @SerialName("empty")
    public data object Empty : CriticalSlotContent
    @Serializable
    @SerialName("engine")
    public data object Engine : CriticalSlotContent
    @Serializable
    @SerialName("gyro")
    public data object Gyro : CriticalSlotContent
    @Serializable
    @SerialName("sensors")
    public data object Sensors : CriticalSlotContent
    @Serializable
    @SerialName("lifeSupport")
    public data object LifeSupport : CriticalSlotContent
    @Serializable
    @SerialName("cockpit")
    public data object Cockpit : CriticalSlotContent
    @Serializable
    @SerialName("actuator")
    public data class Actuator(@SerialName("actuatorType") public val type: ActuatorType) : CriticalSlotContent
    @Serializable
    @SerialName("weaponMount")
    public data class WeaponMount(public val weaponId: WeaponMountId) : CriticalSlotContent
    @Serializable
    @SerialName("ammoBin")
    public data class AmmoBin(
        @SerialName("ammoType") public val type: AmmoType,
        public val shots: Int,
    ) : CriticalSlotContent
    @Serializable
    @SerialName("heatSink")
    public data object HeatSink : CriticalSlotContent
    @Serializable
    @SerialName("jumpJet")
    public data object JumpJet : CriticalSlotContent
}
