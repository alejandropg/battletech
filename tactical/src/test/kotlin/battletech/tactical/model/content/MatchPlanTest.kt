package battletech.tactical.model.content

import battletech.tactical.model.PlayerId
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Covers [MatchPlan]'s accessors/mutators and its wire shape (an enum-keyed roster map). */
internal class MatchPlanTest {

    @Test
    fun `count and totalUnits are zero for an unselected player or variant`() {
        val plan = MatchPlan()

        assertThat(plan.count(PlayerId.PLAYER_1, "WHM-6R")).isZero()
        assertThat(plan.totalUnits(PlayerId.PLAYER_1)).isZero()
    }

    @Test
    fun `withCount sets a positive count`() {
        val plan = MatchPlan().withCount(PlayerId.PLAYER_1, "WHM-6R", 3)

        assertThat(plan.count(PlayerId.PLAYER_1, "WHM-6R")).isEqualTo(3)
        assertThat(plan.totalUnits(PlayerId.PLAYER_1)).isEqualTo(3)
    }

    @Test
    fun `withCount of zero removes the entry entirely, keeping equality with an empty plan`() {
        val plan = MatchPlan().withCount(PlayerId.PLAYER_1, "WHM-6R", 3).withCount(PlayerId.PLAYER_1, "WHM-6R", 0)

        assertThat(plan).isEqualTo(MatchPlan())
    }

    @Test
    fun `withCount of a negative value also removes the entry`() {
        val plan = MatchPlan().withCount(PlayerId.PLAYER_1, "WHM-6R", 3).withCount(PlayerId.PLAYER_1, "WHM-6R", -1)

        assertThat(plan).isEqualTo(MatchPlan())
    }

    @Test
    fun `withCount keeps other variants and players untouched`() {
        val plan = MatchPlan()
            .withCount(PlayerId.PLAYER_1, "WHM-6R", 1)
            .withCount(PlayerId.PLAYER_1, "WVR-6R", 2)
            .withCount(PlayerId.PLAYER_2, "AS7-D", 1)

        assertThat(plan.count(PlayerId.PLAYER_1, "WHM-6R")).isEqualTo(1)
        assertThat(plan.count(PlayerId.PLAYER_1, "WVR-6R")).isEqualTo(2)
        assertThat(plan.count(PlayerId.PLAYER_2, "AS7-D")).isEqualTo(1)
        assertThat(plan.totalUnits(PlayerId.PLAYER_1)).isEqualTo(3)
    }

    @Test
    fun `withMap replaces the map name`() {
        val plan = MatchPlan().withMap("arena")

        assertThat(plan.mapName).isEqualTo("arena")
        assertThat(plan.withMap(null).mapName).isNull()
    }

    @Test
    fun `round-trips through JSON, including the enum-keyed roster map`() {
        val json = Json
        val plan = MatchPlan(mapName = "arena", rosters = mapOf(PlayerId.PLAYER_1 to mapOf("WHM-6R" to 2)))

        val encoded = json.encodeToString(MatchPlan.serializer(), plan)
        val decoded = json.decodeFromString(MatchPlan.serializer(), encoded)

        assertThat(decoded).isEqualTo(plan)
    }
}
