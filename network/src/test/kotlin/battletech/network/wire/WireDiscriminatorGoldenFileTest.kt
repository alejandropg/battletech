package battletech.network.wire

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins every discriminator actually reachable on the wire — the two envelopes
 * ([ClientMessage]/[ServerMessage]) and everything nested under them — against a checked-in
 * golden file, alongside [PROTOCOL_VERSION].
 *
 * [WireDiscriminatorConventionTest] checks the same values are well-formed and unique per root
 * by walking Kotlin's *subtype* graph (`sealedSubclasses`); this test walks the *serialization*
 * graph instead (the actual [SerialDescriptor] tree kotlinx builds from `ClientMessage`/
 * `ServerMessage`'s fields, generics included), so the two catch different blind spots: this one
 * proves a discriminator someone renamed or a type someone stopped reaching from the two message
 * envelopes shows up as a diff here, and puts the required [PROTOCOL_VERSION] bump in the same
 * diff a wire change produces.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class WireDiscriminatorGoldenFileTest {

    @Test
    fun `wire discriminators match the checked-in golden file`() {
        val discriminators = sortedSetOf<String>().apply {
            collectDiscriminators(ClientMessage.serializer().descriptor, mutableSetOf(), this)
            collectDiscriminators(ServerMessage.serializer().descriptor, mutableSetOf(), this)
        }
        val actual = (listOf("PROTOCOL_VERSION=$PROTOCOL_VERSION") + discriminators).joinToString("\n") + "\n"

        val goldenFile = requireNotNull(javaClass.getResource("/wire-discriminators.txt")) {
            "network/src/test/resources/wire-discriminators.txt is missing"
        }
        val expected = goldenFile.readText()

        assertThat(actual).describedAs(
            "the wire's discriminators (or PROTOCOL_VERSION) changed — if intentional, bump " +
                "PROTOCOL_VERSION (network/wire/Messages.kt) if it isn't already bumped for this " +
                "change, then update network/src/test/resources/wire-discriminators.txt to match",
        ).isEqualTo(expected)
    }

    /**
     * Walks [descriptor]'s element graph, collecting every discriminator string a
     * [PolymorphicKind.SEALED] descriptor's synthetic "value" element names. `visited` de-dupes
     * by [SerialDescriptor.serialName] to stop cycles (e.g. `GameEvent` -> `PhysicalAttacksResolved`
     * -> `PhysicalAttackResult.Knockdown.Fell.pilotEvents: List<GameEvent>`) — except for
     * [StructureKind.LIST]/[StructureKind.MAP], whose `serialName` is the constant collection
     * class name (`kotlin.collections.ArrayList`, `...LinkedHashMap`) regardless of element type,
     * so de-duping those by name would wrongly skip every List/Map after the first one walked.
     */
    private fun collectDiscriminators(descriptor: SerialDescriptor, visited: MutableSet<String>, acc: MutableSet<String>) {
        val dedupes = descriptor.kind != StructureKind.LIST && descriptor.kind != StructureKind.MAP
        if (dedupes && !visited.add(descriptor.serialName)) return

        if (descriptor.kind == PolymorphicKind.SEALED) {
            // Element 0 is "type" (the discriminator's own String descriptor); element 1, "value",
            // is a synthetic descriptor whose element NAMES are every registered leaf's serial name
            // (kotlinx.serialization.SealedClassSerializer flattens nested sealed subtrees into it),
            // each paired with that leaf's own descriptor for continued recursion.
            val value = descriptor.getElementDescriptor(1)
            acc += value.elementNames
            for (i in 0 until value.elementsCount) {
                collectDiscriminators(value.getElementDescriptor(i), visited, acc)
            }
            return
        }

        for (i in 0 until descriptor.elementsCount) {
            collectDiscriminators(descriptor.getElementDescriptor(i), visited, acc)
        }
    }
}
