package battletech.tui

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Enforces two invariants stated in root `CLAUDE.md`: locality is an adapter, not a branch (no
 * delivery code outside `main()` may tell hot-seat/host/join/serve apart, which starts with
 * nothing but `Main.kt` knowing `battletech.network` exists at all), and `tui` never depends on
 * `strategic` (a placeholder module, `CLAUDE.md` says to ignore it).
 */
class ArchitectureTest {

    private val mainFiles = Konsist.scopeFromProject()
        .files
        .filter { it.path.contains("/tui/") && !it.path.contains("/test/") }

    @Test
    fun `only Main kt may import battletech network`() {
        mainFiles
            .filter { !it.path.endsWith("/battletech/tui/Main.kt") }
            .forEach { file ->
                val violations = file.imports.filter { it.name.startsWith("battletech.network") }
                assertTrue(
                    violations.isEmpty(),
                    "${file.name} imports battletech.network — only Main.kt may compose a delivery mode: ${violations.map { it.name }}",
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
