package battletech.network.wire

import battletech.tactical.unit.MechModel
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Connection-scoped decoder for host messages.
 *
 * A successful join is one JSON message, but its snapshot contains compact mech-variant
 * references. The implementation therefore reads the bootstrap's full model definitions first,
 * builds a temporary resolver, and only then decodes the complete message. The resolver is
 * committed atomically after successful decoding and reused for every later state push.
 */
internal class ServerMessageDecoder(
    initialFindMech: (String) -> MechModel? = { null },
) {
    private var json: Json = WireJson.createJson(initialFindMech)
    private var receivedBootstrap: Boolean = false

    internal fun decode(line: String): ServerMessage {
        val element = WireJson.json.parseToJsonElement(line)
        val root = element as? JsonObject
            ?: throw SerializationException("Server message must be a JSON object")
        if (root["type"]?.jsonPrimitive?.content != JOIN_ACCEPTED_TYPE) {
            return json.decodeFromJsonElement(element)
        }
        if (receivedBootstrap) {
            throw SerializationException("Host sent more than one match bootstrap")
        }

        val bootstrap = root["bootstrap"] as? JsonObject
            ?: throw SerializationException("JoinAccepted is missing bootstrap")
        val modelsElement = bootstrap["mechModels"]
            ?: throw SerializationException("Match bootstrap is missing mechModels")
        val models = WireJson.json.decodeFromJsonElement<List<MechModel>>(modelsElement)
        val modelsByVariant = linkedMapOf<String, MechModel>()
        for (model in models) {
            if (modelsByVariant.put(model.variant, model) != null) {
                throw SerializationException("Mech variant is repeated in match bootstrap: ${model.variant}")
            }
        }

        val candidateJson = WireJson.createJson(modelsByVariant::get)
        val message = candidateJson.decodeFromJsonElement<ServerMessage>(element)
        if (message !is ServerMessage.JoinAccepted) {
            throw SerializationException("Expected JoinAccepted while decoding match bootstrap")
        }
        json = candidateJson
        receivedBootstrap = true
        return message
    }

    private companion object {
        private const val JOIN_ACCEPTED_TYPE: String = "joinAccepted"
    }
}
