package battletech.tui

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Enforces two invariants stated in root `CLAUDE.md`: locality is an adapter, not a branch (no
 * delivery code outside `main()` may tell hot-seat/host/join/server apart, which starts with
 * nothing but `Main.kt`/`Composition.kt` knowing `battletech.network` exists at all), and `tui`
 * never depends on `strategic` (a placeholder module, `CLAUDE.md` says to ignore it).
 *
 * `Composition.kt` joins `Main.kt` in the allowlist deliberately (see the interactive setup
 * screen plan, `docs/architecture.md`): it holds the `SetupLobby` adapters over
 * `battletech.network` (`LobbyHostAdapter`/`LobbyMirrorAdapter`) that `main()`'s `Mode.Interactive`/
 * `Mode.Join` branches compose, kept out of `Main.kt` only to keep that file from growing past a
 * single screenful of wiring. Both files are still the ONLY two allowed to know the network
 * module exists — everything else in `tui` composes through the `SetupLobby` port instead.
 */
class ArchitectureTest {

    private val mainFiles = Konsist.scopeFromProject()
        .files
        .filter { it.path.contains("/tui/") && !it.path.contains("/test/") }

    // KoFile.name is the base name without extension (confirmed against Konsist's own behavior
    // here) — unlike the path-based check this replaced, which compared the full ".../Main.kt" path.
    private val compositionFileNames = setOf("Main", "Composition")

    @Test
    fun `only Main kt and Composition kt may import battletech network`() {
        mainFiles
            .filter { it.name !in compositionFileNames }
            .forEach { file ->
                val violations = file.imports.filter { it.name.startsWith("battletech.network") }
                assertTrue(
                    violations.isEmpty(),
                    "${file.name} imports battletech.network — only Main.kt/Composition.kt may compose a delivery mode: ${violations.map { it.name }}",
                )
            }
    }

    @Test
    fun `tui does not import battletech strategic`() {
        mainFiles.forEach { file ->
            val violations = file.imports.filter { it.name.startsWith("battletech.strategic") }
            assertTrue(
                violations.isEmpty(),
                "${file.name} imports battletech.strategic: ${violations.map { it.name }}",
            )
        }
    }
}
