package tenter

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Enforces `tenter`'s one architectural promise: it is a standalone terminal-UI toolkit that
 * knows nothing about BattleTech and depends on nothing beyond Kotlin/kotlinx/Mordant. This is
 * what keeps a future extraction into its own library viable — a single BattleTech import
 * anywhere in this module would silently reattach it to the game.
 */
class ArchitectureTest {

    private val mainFiles = Konsist.scopeFromProject()
        .files
        .filter { it.path.contains("/tenter/") && !it.path.contains("/test/") }

    private val allowedImportPrefixes = listOf(
        "tenter",
        "kotlin",
        "kotlinx",
        "java",
        "com.github.ajalt",
    )

    @Test
    fun `tenter does not import battletech code`() {
        mainFiles.forEach { file ->
            val violations = file.imports.filter { it.name.startsWith("battletech.") }
            assertTrue(
                violations.isEmpty(),
                "${file.name} imports battletech code: ${violations.map { it.name }}",
            )
        }
    }

    @Test
    fun `tenter only imports from an approved set of packages`() {
        mainFiles.forEach { file ->
            val violations = file.imports.filter { imp -> allowedImportPrefixes.none { imp.name.startsWith(it) } }
            assertTrue(
                violations.isEmpty(),
                "${file.name} imports outside the approved set: ${violations.map { it.name }}",
            )
        }
    }
}
