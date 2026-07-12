package battletech.network.wire

import battletech.network.wire.SessionId.ALPHABET
import battletech.network.wire.SessionId.normalize
import java.security.SecureRandom

/**
 * 6-character session identifiers shown in the host's log/stdout and typed
 * in by the joining player. Case-insensitive; the [ALPHABET] excludes
 * visually ambiguous characters (0/O, 1/I/L) to reduce transcription errors.
 */
public object SessionId {

    public const val ALPHABET: String = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    private const val LENGTH: Int = 6

    /** Generates a new random 6-character session id from [ALPHABET]. */
    public fun generate(random: SecureRandom = SecureRandom()): String =
        buildString(LENGTH) {
            repeat(LENGTH) {
                append(ALPHABET[random.nextInt(ALPHABET.length)])
            }
        }

    /** Trims whitespace and uppercases [value] for comparison/storage. */
    public fun normalize(value: String): String = value.trim().uppercase()

    /** True if [a] and [b] denote the same session id once [normalize]d. */
    public fun matches(a: String, b: String): Boolean = normalize(a) == normalize(b)
}
