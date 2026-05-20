package battletech.tactical

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArchitectureTest {

    private val mainFiles = Konsist.scopeFromProject()
        .files
        .filter { !it.path.contains("/test/") }

    @Test
    fun `attack package does not import from movement package`() {
        mainFiles
            .filter { it.packagee?.name?.startsWith("battletech.tactical.attack") == true }
            .forEach { file ->
                val violations = file.imports.filter { it.name.startsWith("battletech.tactical.movement") }
                assertTrue(violations.isEmpty(),
                    "${file.name} imports from movement/: ${violations.map { it.name }}")
            }
    }

    @Test
    fun `movement package does not import from attack package`() {
        mainFiles
            .filter { it.packagee?.name?.startsWith("battletech.tactical.movement") == true }
            .forEach { file ->
                val violations = file.imports.filter { it.name.startsWith("battletech.tactical.attack") }
                assertTrue(violations.isEmpty(),
                    "${file.name} imports from attack/: ${violations.map { it.name }}")
            }
    }

    @Test
    fun `model package does not import from verticals, session, or query`() {
        val forbidden = listOf(
            "battletech.tactical.movement",
            "battletech.tactical.attack",
            "battletech.tactical.session",
            "battletech.tactical.query",
        )
        mainFiles
            .filter { it.packagee?.name?.startsWith("battletech.tactical.model") == true }
            .forEach { file ->
                val violations = file.imports.filter { imp -> forbidden.any { imp.name.startsWith(it) } }
                assertTrue(violations.isEmpty(),
                    "${file.name} imports from forbidden packages: ${violations.map { it.name }}")
            }
    }

    @Test
    fun `dice package does not import from verticals, session, or query`() {
        val forbidden = listOf(
            "battletech.tactical.movement",
            "battletech.tactical.attack",
            "battletech.tactical.session",
            "battletech.tactical.query",
        )
        mainFiles
            .filter { it.packagee?.name?.startsWith("battletech.tactical.dice") == true }
            .forEach { file ->
                val violations = file.imports.filter { imp -> forbidden.any { imp.name.startsWith(it) } }
                assertTrue(violations.isEmpty(),
                    "${file.name} imports from forbidden packages: ${violations.map { it.name }}")
            }
    }
}
