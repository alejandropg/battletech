package battletech.tactical.model.mech

import battletech.tactical.model.MechLocation
import battletech.tactical.unit.AmmoType
import battletech.tactical.unit.ArmorLayout
import battletech.tactical.unit.CriticalLayoutBuilder
import battletech.tactical.unit.HeatSink
import battletech.tactical.unit.HeatSinkType
import battletech.tactical.unit.InternalStructureTables
import battletech.tactical.unit.MechModel
import battletech.tactical.unit.WeaponModels
import battletech.tactical.unit.validate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Strict on-disk shape of one collection containing one or more mech variants. */
@Serializable
internal data class MechFile(
    internal val models: List<ModelSpec>,
) {
    internal fun toModels(source: String): List<MechModel> = models.map { it.toModel(source) }

    @Serializable
    internal data class ModelSpec(
        internal val variant: String,
        internal val name: String,
        internal val tonnage: Int,
        internal val walkingMP: Int,
        internal val runningMP: Int,
        internal val jumpMP: Int = 0,
        internal val heatSinks: HeatSink = HeatSink(HeatSinkType.STS, 10),
        internal val armor: ArmorLayout,
        internal val loadout: List<LoadoutSpec> = emptyList(),
    ) {
        internal fun toModel(source: String): MechModel {
            if (variant.isBlank()) throw invalid(source, "variant must not be blank")
            if (name.isBlank()) throw invalid(source, "name must not be blank")
            if (walkingMP < 0) throw invalid(source, "walkingMP must not be negative, was $walkingMP")
            if (runningMP < 0) throw invalid(source, "runningMP must not be negative, was $runningMP")
            if (jumpMP < 0) throw invalid(source, "jumpMP must not be negative, was $jumpMP")
            if (heatSinks.units < 10) {
                throw invalid(source, "heatSinks.units must be at least 10, was ${heatSinks.units}")
            }
            validateArmor(source)

            return try {
                val internalStructure = InternalStructureTables.forTonnage(tonnage)
                val builder = CriticalLayoutBuilder()
                loadout.forEach { it.apply(builder, source, variant) }
                builder.heatSinks(heatSinks.units - 10)
                builder.jumpJets(jumpMP)
                val built = builder.build()
                built.layout.validate(built.weapons)
                MechModel(
                    variant = variant,
                    name = name,
                    tonnage = tonnage,
                    walkingMP = walkingMP,
                    runningMP = runningMP,
                    jumpMP = jumpMP,
                    heatSink = heatSinks,
                    armor = armor,
                    internalStructure = internalStructure,
                    criticalLayout = built.layout,
                    weapons = built.weapons,
                )
            } catch (e: MechLoadException) {
                throw e
            } catch (e: IllegalArgumentException) {
                throw invalid(source, e.message ?: "invalid value", e)
            } catch (e: IllegalStateException) {
                throw invalid(source, e.message ?: "invalid loadout", e)
            }
        }

        private fun validateArmor(source: String) {
            val values = listOf(
                armor.head,
                armor.centerTorso,
                armor.centerTorsoRear,
                armor.leftTorso,
                armor.leftTorsoRear,
                armor.rightTorso,
                armor.rightTorsoRear,
                armor.leftArm,
                armor.rightArm,
                armor.leftLeg,
                armor.rightLeg,
            )
            if (values.any { it < 0 }) throw invalid(source, "armor values must not be negative")
        }

        private fun invalid(source: String, detail: String, cause: Throwable? = null): MechLoadException =
            MechLoadException("Invalid mech model '$variant' in $source: $detail", cause)
    }

    @Serializable
    internal sealed interface LoadoutSpec {
        fun apply(builder: CriticalLayoutBuilder, source: String, variant: String)

        @Serializable
        @SerialName("weapon")
        public data class Weapon(
            public val location: MechLocation,
            public val weapon: String,
        ) : LoadoutSpec {
            public override fun apply(builder: CriticalLayoutBuilder, source: String, variant: String) {
                val model = WeaponModels.find(weapon)
                    ?: throw MechLoadException(
                        "Invalid mech model '$variant' in $source: unknown weapon '$weapon'. " +
                            "Known weapons: ${WeaponModels.ids.sorted().joinToString(", ")}",
                    )
                builder.place(location, model)
            }
        }

        @Serializable
        @SerialName("ammo")
        public data class Ammo(
            public val location: MechLocation,
            public val ammo: AmmoType,
            public val tons: Int = 1,
        ) : LoadoutSpec {
            public override fun apply(builder: CriticalLayoutBuilder, source: String, variant: String) {
                if (tons <= 0) {
                    throw MechLoadException(
                        "Invalid mech model '$variant' in $source: ammo tons must be positive, was $tons",
                    )
                }
                builder.ammo(location, ammo, tons)
            }
        }

        @Serializable
        @SerialName("omitActuators")
        public data class OmitActuators(
            public val location: MechLocation,
            public val lowerArm: Boolean = false,
            public val hand: Boolean = false,
        ) : LoadoutSpec {
            public override fun apply(builder: CriticalLayoutBuilder, source: String, variant: String) {
                if (location != MechLocation.LEFT_ARM && location != MechLocation.RIGHT_ARM) {
                    throw MechLoadException(
                        "Invalid mech model '$variant' in $source: actuators can only be omitted from an arm",
                    )
                }
                if (!lowerArm && !hand) {
                    throw MechLoadException(
                        "Invalid mech model '$variant' in $source: omitActuators must select lowerArm or hand",
                    )
                }
                builder.omitActuators(location, lowerArm, hand)
            }
        }
    }

    internal companion object {
        internal fun decode(json: Json, text: String): MechFile = json.decodeFromString(text)
    }
}
