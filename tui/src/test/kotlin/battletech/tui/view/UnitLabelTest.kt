package battletech.tui.view

import battletech.tactical.unit.UnitId
import battletech.tui.aUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class UnitLabelTest {

    @Test
    fun `of id and name formats id- name`() {
        assertEquals("W2: Wolverine WVR-6R", UnitLabel.of(UnitId("W2"), "Wolverine WVR-6R"))
    }

    @Test
    fun `of unit delegates to its id and name`() {
        val unit = aUnit(id = "W2", name = "Wolverine WVR-6R")
        assertEquals("W2: Wolverine WVR-6R", UnitLabel.of(unit))
    }
}
