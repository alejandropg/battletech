package battletech.tactical.model.content

import battletech.tactical.model.GameMap
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.unit.MechModel
import battletech.tactical.unit.MechModels
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Covers [AssetRegistry.merge]'s rules — see its KDoc — plus [AssetRegistry.forMatch] and [AssetBundle.duplicateId]. */
internal class AssetRegistryTest {

    private val origin: HexCoordinates = HexCoordinates(0, 0)
    private val arena: GameMap = GameMap(hexes = mapOf(origin to Hex(origin)), name = "arena")
    private val atlas: MechModel = MechModels["AS7-D"]
    private val warhammer: MechModel = MechModels["WHM-6R"]

    @Test
    fun `adds an asset absent from the registry, with no finding`() {
        val merged = AssetRegistry.EMPTY.merge(AssetBundle(maps = listOf(arena)))

        assertThat(merged.registry.map("arena")).isEqualTo(arena)
        assertThat(merged.conflicts).isEmpty()
    }

    @Test
    fun `identical re-registration is a no-op with no finding`() {
        val registry = AssetRegistry.EMPTY.merge(AssetBundle(mechs = listOf(atlas))).registry

        val merged = registry.merge(AssetBundle(mechs = listOf(atlas)))

        assertThat(merged.registry).isEqualTo(registry)
        assertThat(merged.conflicts).isEmpty()
    }

    @Test
    fun `same id with different content keeps the first entry and reports one conflict`() {
        val registry = AssetRegistry.EMPTY.merge(AssetBundle(mechs = listOf(atlas))).registry
        val drifted = atlas.copy(name = "${atlas.name} (drifted)")

        val merged = registry.merge(AssetBundle(mechs = listOf(drifted)))

        assertThat(merged.registry.mech(atlas.variant)).isEqualTo(atlas)
        assertThat(merged.conflicts).containsExactly(AssetRef(AssetKind.MECH, atlas.variant))
    }

    @Test
    fun `equality ignores the id field itself`() {
        // The stored entry's own `name` field disagrees with the key it's registered under —
        // constructed directly, since a normal merge always keys an asset by its own id.
        val storedUnderDifferentName = arena.copy(name = "not-the-key")
        val registry = AssetRegistry(maps = mapOf("arena" to storedUnderDifferentName))

        val merged = registry.merge(AssetBundle(maps = listOf(arena)))

        assertThat(merged.conflicts).isEmpty()
        assertThat(merged.registry).isEqualTo(registry)
    }

    @Test
    fun `conflicts come back sorted by kind then id`() {
        val seed = AssetBundle(maps = listOf(arena), mechs = listOf(atlas, warhammer))
        val registry = AssetRegistry.EMPTY.merge(seed).registry
        val drifted = AssetBundle(
            maps = listOf(arena.copy(hexes = emptyMap())),
            mechs = listOf(
                warhammer.copy(name = "${warhammer.name} (drifted)"),
                atlas.copy(name = "${atlas.name} (drifted)"),
            ),
        )

        val merged = registry.merge(drifted)

        assertThat(merged.conflicts).containsExactly(
            AssetRef(AssetKind.MAP, arena.name),
            AssetRef(AssetKind.MECH, atlas.variant),
            AssetRef(AssetKind.MECH, warhammer.variant),
        )
    }

    @Test
    fun `blank-id assets are skipped silently`() {
        val merged = AssetRegistry.EMPTY.merge(AssetBundle(maps = listOf(arena.copy(name = ""))))

        assertThat(merged.registry.maps).isEmpty()
        assertThat(merged.conflicts).isEmpty()
    }

    @Test
    fun `forMatch yields the board plus exactly the roster's distinct models`() {
        val state = ContentCatalog.load().resolveGame()

        val registry = AssetRegistry.forMatch(state)

        assertThat(registry.map(state.map.name)).isEqualTo(state.map)
        assertThat(registry.mechs.keys).isEqualTo(state.units.map { it.variant }.toSet())
        state.units.forEach { unit -> assertThat(registry.mech(unit.variant)).isEqualTo(unit.model) }
    }

    @Test
    fun `forMatch skips the board when its name is blank`() {
        val state = ContentCatalog.load().resolveGame().let { it.copy(map = it.map.copy(name = "")) }

        val registry = AssetRegistry.forMatch(state)

        assertThat(registry.maps).isEmpty()
    }

    // ---------- AssetBundle.duplicateId ----------

    @Test
    fun `duplicateId names the repeated id and its kind`() {
        val bundle = AssetBundle(mechs = listOf(atlas, atlas.copy(name = "renamed")))

        assertThat(bundle.duplicateId()).isEqualTo(AssetRef(AssetKind.MECH, atlas.variant))
    }

    @Test
    fun `duplicateId is null for a well-formed bundle, and ignores repeated blank ids`() {
        val wellFormed = AssetBundle(maps = listOf(arena), mechs = listOf(atlas, warhammer))
        val blanks = AssetBundle(maps = listOf(arena.copy(name = ""), arena.copy(name = "")))

        assertThat(wellFormed.duplicateId()).isNull()
        assertThat(blanks.duplicateId()).isNull()
    }

    @Test
    fun `merge is total - a bundle repeating an id merges as if two parties had contributed it`() {
        val bundle = AssetBundle(mechs = listOf(atlas, atlas.copy(name = "renamed")))

        val merged = AssetRegistry.EMPTY.merge(bundle)

        assertThat(merged.registry.mech(atlas.variant)).isEqualTo(atlas)
        assertThat(merged.conflicts).containsExactly(AssetRef(AssetKind.MECH, atlas.variant))
    }
}
