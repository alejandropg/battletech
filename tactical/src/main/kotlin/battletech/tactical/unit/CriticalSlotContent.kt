package battletech.tactical.unit

public sealed interface CriticalSlotContent {
    public data object Empty : CriticalSlotContent
    public data object Engine : CriticalSlotContent
    public data object Gyro : CriticalSlotContent
    public data object Sensors : CriticalSlotContent
    public data object LifeSupport : CriticalSlotContent
    public data object Cockpit : CriticalSlotContent
    public data class Actuator(public val type: ActuatorType) : CriticalSlotContent
    public data class WeaponMount(public val weaponId: WeaponMountId) : CriticalSlotContent
    public data class AmmoBin(public val type: AmmoType, public val shots: Int) : CriticalSlotContent
    public data object HeatSink : CriticalSlotContent
    public data object JumpJet : CriticalSlotContent
}
