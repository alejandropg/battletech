package battletech.network.wire

import battletech.network.wire.WireJson.encodeToLine
import kotlinx.serialization.json.Json

/**
 * Shared [Json] configuration for the wire protocol, plus line-oriented
 * encode/decode helpers. Messages are newline-delimited on the socket;
 * by convention the encode helpers here return the JSON text WITHOUT a
 * trailing newline — the socket layer (writer thread) is responsible for
 * appending `'\n'` before writing, and the reader consumes lines already
 * stripped of their terminator (e.g. via `BufferedReader.readLine()`).
 *
 * `allowStructuredMapKeys = true` is LOAD-BEARING: [battletech.tactical.model.GameMap.hexes]
 * is a `Map<HexCoordinates, Hex>`, and `HexCoordinates` is not a primitive/enum key type, so
 * without this flag encoding throws.
 */
public object WireJson {

    public val json: Json = Json {
        allowStructuredMapKeys = true
        classDiscriminator = "type"
    }

    /** Encodes [msg] to a single line of JSON (no trailing newline — see class doc). */
    public fun encodeToLine(msg: ClientMessage): String = json.encodeToString(msg)

    /** Encodes [msg] to a single line of JSON (no trailing newline — see class doc). */
    public fun encodeToLine(msg: ServerMessage): String = json.encodeToString(msg)

    /** Decodes a line previously produced by [encodeToLine] back into a [ClientMessage]. */
    public fun decodeClientMessage(line: String): ClientMessage = json.decodeFromString(line)

    /** Decodes a line previously produced by [encodeToLine] back into a [ServerMessage]. */
    public fun decodeServerMessage(line: String): ServerMessage = json.decodeFromString(line)
}
