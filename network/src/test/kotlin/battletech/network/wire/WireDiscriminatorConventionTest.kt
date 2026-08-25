package battletech.network.wire

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.provider.hasAnnotationOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * Enforces the wire-discriminator convention documented in `docs/architecture.md`: a concrete
 * variant's `@SerialName` is the case-decapitalized dotted tail of its own lexical nesting path
 * (e.g. `RuleRejection.NotAdjacent` -> `"notAdjacent"`, `GameEvent`'s `UnitStoodUp.Detailed` ->
 * `"unitStoodUp.detailed"`), and is unique among every other variant reachable from the same
 * `@Serializable sealed` root.
 *
 * This replaces hand-maintained discipline (the previous state: some hierarchies annotated,
 * most not, with no test catching either a missing annotation or a value someone just made up)
 * with a build-enforced rule: the value must be grounded in the class's actual declaration, and
 * every root's variants must not collide.
 *
 * Discovery is mechanical on both ends, not a hand-maintained list — that list is exactly what
 * went stale before (see `docs/architecture.md`'s wire-discriminator section):
 *  - Konsist finds every top-level `@Serializable sealed` interface/class under `battletech.`.
 *  - Reflection (`sealedSubclasses`) walks each one down to its concrete leaves.
 */
internal class WireDiscriminatorConventionTest {

    /**
     * Fail-open guard, matching [battletech.tactical.ArchitectureTest]'s pattern: if the Konsist
     * query below ever matched zero classes (a Konsist API change, a filter typo), every test
     * below would pass vacuously. Pin a few roots known to exist across both wire-crossing
     * modules so that regression is caught here instead.
     */
    @Test
    fun `discovers the known wire-crossing sealed roots`() {
        val rootNames = Roots.all.map { it.simpleName }.toSet()
        assertTrue(Roots.all.isNotEmpty(), "Konsist found zero top-level @Serializable sealed declarations — query is broken")
        listOf("GameEvent", "GameCommand", "CommandRejection", "RuleRejection", "CommandResult", "ClientMessage", "ServerMessage")
            .forEach { assertTrue(it in rootNames, "expected $it among discovered roots, found $rootNames") }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rootNames")
    fun `every concrete variant carries a SerialName grounded in its own declaration`(rootName: String) {
        val leaves = concreteLeavesOf(Roots.byName(rootName))

        val missing = leaves.filter { it.findAnnotation<SerialName>() == null }
        assertTrue(missing.isEmpty(), "$rootName: missing @SerialName on ${missing.map { it.qualifiedName }}")

        val invalid = leaves.mapNotNull { leaf ->
            val serialName = leaf.findAnnotation<SerialName>()!!.value
            if (serialName in validSerialNameCandidates(leaf)) null else leaf.qualifiedName to serialName
        }
        assertTrue(
            invalid.isEmpty(),
            "$rootName: @SerialName value isn't a decapitalized suffix of its own lexical nesting path: $invalid",
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rootNames")
    fun `every root's variants have pairwise-unique SerialName values`(rootName: String) {
        val leaves = concreteLeavesOf(Roots.byName(rootName))
        val bySerialName = leaves.groupBy { it.findAnnotation<SerialName>()?.value }

        val duplicates = bySerialName.filterValues { it.size > 1 }
        assertTrue(
            duplicates.isEmpty(),
            "$rootName: duplicate @SerialName values would collide on the wire: " +
                duplicates.mapValues { (_, dupes) -> dupes.map { it.qualifiedName } },
        )
    }

    /** Recursively descends a sealed hierarchy to its concrete (non-sealed) leaves. */
    private fun concreteLeavesOf(kClass: KClass<*>): List<KClass<*>> =
        if (kClass.isSealed) kClass.sealedSubclasses.flatMap { concreteLeavesOf(it) } else listOf(kClass)

    /**
     * Every dotted, decapitalized trailing-segment suffix of [leaf]'s lexical nesting path
     * relative to its package — e.g. for `battletech.tactical.attack.AttackResult.SingleHit`,
     * `{"singleHit", "attackResult.singleHit"}`. A conforming `@SerialName` must be one of these:
     * grounded in the real declaration, never an arbitrary string, while still allowing a bare
     * leaf name where nothing else in its root collides.
     */
    private fun validSerialNameCandidates(leaf: KClass<*>): Set<String> {
        val packageName = leaf.java.`package`?.name.orEmpty()
        val segments = leaf.qualifiedName!!.removePrefix("$packageName.").split(".")
        val decapitalized = segments.map { it.replaceFirstChar(Char::lowercaseChar) }
        return (1..decapitalized.size).map { tailLength -> decapitalized.takeLast(tailLength).joinToString(".") }.toSet()
    }

    /** Discovers, once, every top-level `@Serializable sealed` declaration under `battletech.`. */
    private object Roots {
        val all: List<KClass<*>> by lazy {
            Konsist.scopeFromProject()
                .classesAndInterfaces(includeNested = true)
                .filter {
                    it.isTopLevel &&
                        it.hasSealedModifier &&
                        it.hasAnnotationOf<Serializable>() &&
                        it.packagee?.name?.startsWith("battletech.") == true
                }
                .map { Class.forName(it.fullyQualifiedName!!).kotlin }
        }

        fun byName(simpleName: String): KClass<*> = all.single { it.simpleName == simpleName }
    }

    companion object {
        /** Every discovered root's simple name, as a JUnit method-source. */
        @JvmStatic
        private fun rootNames(): Stream<Arguments> = Roots.all.map { Arguments.of(it.simpleName) }.stream()
    }
}
