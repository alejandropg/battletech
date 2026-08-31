package battletech.network.wire

import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.model.content.ContentCatalog
import battletech.tactical.query.projectFor
import battletech.tactical.session.TurnState
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ServerMessageDecoderTest {

    private val game = ContentCatalog.load().resolveGame()
    private val snapshot = GameSnapshot(
        units = game.projectFor(PlayerId.PLAYER_1).units,
        turnState = TurnState.NULL,
        currentPhase = TurnPhase.INITIATIVE,
        activePlayer = null,
        isMatchOver = false,
    )
    private val bootstrap = MatchBootstrap(
        playerId = PlayerId.PLAYER_1,
        mechModels = game.units.map { it.model }.distinctBy { it.variant },
        map = game.map,
        snapshot = snapshot,
        log = emptyList(),
    )
    private val accepted = ServerMessage.JoinAccepted(bootstrap)
    private val acceptedLine = WireJson.encodeToLine(accepted)

    @Test
    fun `decode bootstrap before compact model references and reuse its catalog for later pushes`() {
        val decoder = ServerMessageDecoder()
        val push = ServerMessage.StatePush(entries = emptyList(), snapshot = snapshot)

        val decodedBootstrap = decoder.decode(acceptedLine)
        val decodedPush = decoder.decode(WireJson.encodeToLine(push))

        assertThat(decodedBootstrap).isEqualTo(accepted)
        assertThat(decodedPush).isEqualTo(push)
    }

    @Test
    fun `reject repeated bootstrap variants before installing the catalog`() {
        val decoder = ServerMessageDecoder()
        val root = WireJson.json.parseToJsonElement(acceptedLine).jsonObject
        val bootstrapObject = root.getValue("bootstrap").jsonObject
        val models = bootstrapObject.getValue("mechModels").jsonArray
        val repeatedModels = JsonArray(listOf(models.first(), models.first()))
        val repeatedBootstrap = JsonObject(bootstrapObject + ("mechModels" to repeatedModels))
        val repeatedLine = JsonObject(root + ("bootstrap" to repeatedBootstrap)).toString()

        val error = assertThrows<SerializationException> { decoder.decode(repeatedLine) }

        assertThat(error).hasMessageContaining("repeated in match bootstrap")
        assertThat(decoder.decode(acceptedLine)).isEqualTo(accepted)
    }

    @Test
    fun `reject unknown snapshot variant without committing a partial catalog`() {
        val decoder = ServerMessageDecoder()
        val referencedVariant = game.units.of(PlayerId.PLAYER_1).first().variant
        val unknownLine = acceptedLine.replace(
            oldValue = "\"model\":\"$referencedVariant\"",
            newValue = "\"model\":\"UNKNOWN-VARIANT\"",
        )
        check(unknownLine != acceptedLine) { "fixture did not contain a compact model reference" }

        val error = assertThrows<SerializationException> { decoder.decode(unknownLine) }

        assertThat(error).hasMessageContaining("Unknown mech variant: UNKNOWN-VARIANT")
        assertThat(decoder.decode(acceptedLine)).isEqualTo(accepted)
    }

    @Test
    fun `reject a second bootstrap on the same connection`() {
        val decoder = ServerMessageDecoder()
        decoder.decode(acceptedLine)

        val error = assertThrows<SerializationException> { decoder.decode(acceptedLine) }

        assertThat(error).hasMessageContaining("more than one match bootstrap")
    }

    @Test
    fun `reject repeated variants when constructing an in-memory bootstrap`() {
        val model = bootstrap.mechModels.first()

        val error = assertThrows<IllegalArgumentException> {
            bootstrap.copy(mechModels = listOf(model, model))
        }

        assertThat(error).hasMessageContaining("repeated in match bootstrap")
    }
}
