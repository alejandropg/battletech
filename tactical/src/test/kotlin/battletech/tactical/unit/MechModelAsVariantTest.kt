package battletech.tactical.unit

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class MechModelAsVariantTest {

    @Test
    fun `encodes as just the variant string`() {
        val json = Json.encodeToString(MechModelAsVariant, MechModels["AS7-D"])

        assertThat(json).isEqualTo("\"AS7-D\"")
    }

    @Test
    fun `decoding round-trips back to the same registry entry`() {
        val encoded = Json.encodeToString(MechModelAsVariant, MechModels["AS7-D"])

        val decoded = Json.decodeFromString(MechModelAsVariant, encoded)

        assertThat(decoded).isEqualTo(MechModels["AS7-D"])
    }

    @Test
    fun `decoding an unknown variant is a decode failure, not a crash`() {
        assertThatThrownBy { Json.decodeFromString(MechModelAsVariant, "\"NOT-A-REAL-MECH\"") }
            .isInstanceOf(SerializationException::class.java)
            .hasMessageContaining("NOT-A-REAL-MECH")
    }
}
