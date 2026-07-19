package battletech.tui.view

import battletech.tactical.unit.UnitId
import battletech.tactical.unit.VisibleUnit

/**
 * Canonical "id: unit name" text used wherever a unit is referred to by both fields
 * (e.g. "W2: Wolverine WVR-6R"). Pure formatting — no styling, no I/O; callers keep
 * composing their own colors, tags, and icons around the returned string.
 */
internal object UnitLabel {
    fun of(id: UnitId, name: String): String = "${id.value}: $name"
    fun of(unit: VisibleUnit): String = of(unit.id, unit.name)
}
