package tenter

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Enforces the internal layering `docs/architecture.md` describes for this module —
 * `text -> screen -> view -> widget/panel`, never the reverse — as an allowed-dependency matrix
 * between `tenter`'s direct child packages. [ArchitectureTest] enforces the module's *external*
 * seam (no `battletech.*` import, nothing outside an approved third-party allowlist); this
 * enforces the seams *between* its own packages, which nothing previously checked — a
 * `tenter.screen` file importing `tenter.panel` passed every existing test.
 */
class LayeringTest {

    private val mainFiles = Konsist.scopeFromProject()
        .files
        .filter { it.path.contains("/tenter/") && !it.path.contains("/test/") }

    private val allowed: Map<String, Set<String>> = mapOf(
        "text" to emptySet(),
        "input" to emptySet(),
        "screen" to setOf("text"),
        "terminal" to setOf("input"),
        "view" to setOf("input", "screen", "text"),
        "widget" to setOf("screen", "text", "view"),
        "panel" to setOf("screen", "view"),
    )

    /** The direct child of `tenter` [packageName] belongs to, e.g. `tenter.view.scrolling` -> `"view"`. */
    private fun topPackage(packageName: String): String =
        packageName.removePrefix("tenter.").substringBefore('.')

    @Test
    fun `every package in the layering matrix has at least one file`() {
        allowed.keys.forEach { pkg ->
            val files = mainFiles.filter { it.packagee?.name?.startsWith("tenter.$pkg") == true }
            assertTrue(
                files.isNotEmpty(),
                "no files found under tenter.$pkg — a rename would silently disable its matrix row",
            )
        }
    }

    @Test
    fun `every package only imports from its declared layer`() {
        mainFiles.forEach { file ->
            val filePackage = file.packagee?.name ?: return@forEach
            if (!filePackage.startsWith("tenter.")) return@forEach // e.g. root `tenter` package files
            val pkg = topPackage(filePackage)
            val allowedTargets = allowed[pkg] ?: error("tenter.$pkg has no entry in the layering matrix — add one")
            val violations = file.imports.filter { imp ->
                imp.name.startsWith("tenter.") &&
                    topPackage(imp.name) != pkg &&
                    topPackage(imp.name) !in allowedTargets
            }
            assertTrue(
                violations.isEmpty(),
                "${file.name} (tenter.$pkg) imports outside its declared layer: ${violations.map { it.name }}",
            )
        }
    }
}
