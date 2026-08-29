package battletech.tactical.unit

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes a [MechModel] as just its [MechModel.variant] on the wire, resolving it back
 * through the supplied [find] function on the way in. A client installs the host's match catalog
 * before decoding any snapshots, so shipping
 * the full chassis blob (armor, internal structure, ~78 critical slots, weapons) on every
 * [CombatUnit] would duplicate data the receiver already has, without conveying anything the
 * variant string doesn't already say.
 *
 * A variant absent from the receiver's match catalog is a wire protocol error, not a crash: it
 * surfaces as [SerializationException] at the decode seam.
 */
public class MechModelAsVariant(
    private val find: (String) -> MechModel?,
) : KSerializer<MechModel> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("battletech.tactical.unit.MechModel", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: MechModel): Unit =
        encoder.encodeString(value.variant)

    override fun deserialize(decoder: Decoder): MechModel {
        val variant = decoder.decodeString()
        return find(variant)
            ?: throw SerializationException("Unknown mech variant: $variant")
    }
}
