package battletech.tactical

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Enforces the allowed-dependency matrix between the direct child packages of
 * `battletech.tactical` (a file under `attack/physical/`, `attack/weapon/`, `model/map/`, or
 * `model/game/` counts as its parent for this purpose; only the first path
 * segment after `battletech.tactical.` matters).
 *
 * Two edges are allowed but are real cycles, not oversights — see [allowed]'s comments for why.
 * Everything else unlisted is a violation: this replaced four narrower prohibition tests (attack
 * ⊥ movement, model/dice as leaves) that verified only those specific edges and left the rest of
 * the graph — including `attack ⇄ query`, closed by moving `RuleResult`/`Warning`/`RuleRejection`
 * into `rules/` — unchecked.
 */
class ArchitectureTest {

    private val mainFiles = Konsist.scopeFromProject()
        .files
        .filter { it.packagee?.name?.startsWith("battletech.tactical.") == true && !it.path.contains("/test/") }

    private val allowed: Map<String, Set<String>> = mapOf(
        "dice" to emptySet(),
        "io" to emptySet(),
        "rules" to setOf("model", "unit"),
        "model" to setOf("io", "unit"),
        "unit" to setOf("dice", "model"),
        // heat <-> attack is mutual by nature: weapon fire generates heat (attack -> heat);
        // heat-phase resolution causes ammo cook-off and pilot hits (heat -> attack).
        "heat" to setOf("attack", "dice", "model", "rules", "session", "unit"),
        // movement/attack -> session is the PhaseHandler strategy pattern: the handler lives
        // with the rules it drives (MovementPhaseHandler, WeaponAttackPhaseHandler,
        // PhysicalAttackPhaseHandler), BattleSession registers and drives it. PhaseOutcome
        // carries GameEvent/TurnState, so the SPI can't be extracted without pulling session
        // types with it. See docs/architecture.md.
        "movement" to setOf("dice", "heat", "model", "rules", "session", "unit"),
        "attack" to setOf("dice", "heat", "model", "rules", "session", "unit"),
        "query" to setOf("attack", "dice", "model", "movement", "rules", "session", "unit"),
        "session" to setOf("attack", "dice", "heat", "model", "movement", "query", "rules", "unit"),
    )

    /** The direct child of `battletech.tactical` [packageName] belongs to, e.g.
     *  `battletech.tactical.attack.physical` -> `"attack"`. */
    private fun topPackage(packageName: String): String =
        packageName.removePrefix("battletech.tactical.").substringBefore('.')

    /**
     * Fail-open guard: the four prohibition tests this replaced passed vacuously if their package
     * filter matched zero files, so a package rename could silently disable a rule. Every package
     * named in [allowed] must have at least one real file backing it.
     */
    @Test
    fun `every package in the allowed-dependency matrix has at least one file`() {
        allowed.keys.forEach { pkg ->
            val files = mainFiles.filter { topPackage(it.packagee!!.name) == pkg }
            assertTrue(
                files.isNotEmpty(),
                "no files found under battletech.tactical.$pkg — a rename would silently disable its matrix row",
            )
        }
    }

    @Test
    fun `every package only imports from its declared allowed set`() {
        mainFiles.forEach { file ->
            val pkg = topPackage(file.packagee!!.name)
            val allowedTargets = allowed[pkg]
                ?: error("battletech.tactical.$pkg has no entry in the allowed-dependency matrix — add one")
            val violations = file.imports.filter { imp ->
                imp.name.startsWith("battletech.tactical.") &&
                    topPackage(imp.name) != pkg &&
                    topPackage(imp.name) !in allowedTargets
            }
            assertTrue(
                violations.isEmpty(),
                "${file.name} (battletech.tactical.$pkg) imports outside its allowed set: ${violations.map { it.name }}",
            )
        }
    }
}
