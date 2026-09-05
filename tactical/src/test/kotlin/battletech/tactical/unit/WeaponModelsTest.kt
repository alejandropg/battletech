package battletech.tactical.unit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class WeaponModelsTest {

    @Test
    fun `findByName round-trips every model in the registry`() {
        val models = WeaponModels.ids.map { WeaponModels.find(it)!! }
        assertEquals(12, models.size, "the registry should still hold twelve models")
        models.forEach { model ->
            assertEquals(model, WeaponModels.findByName(model.name), "findByName missed ${model.name}")
        }
    }

    @Test
    fun `findByName returns null for a name no model carries`() {
        // "Med Laser" is the near-miss worth pinning: the real display name is "Medium Laser", and
        // a resolved attack carries the display name verbatim.
        assertNull(WeaponModels.findByName("Med Laser"))
        assertNull(WeaponModels.findByName("mediumLaser"), "the mech-file id is not the display name")
        assertNull(WeaponModels.findByName(""))
    }

    @Test
    fun `display names are unique, which is what makes findByName unambiguous`() {
        val names = WeaponModels.ids.map { WeaponModels.find(it)!!.name }
        assertEquals(names.size, names.toSet().size, "duplicate display names would shadow a model")
    }
}
