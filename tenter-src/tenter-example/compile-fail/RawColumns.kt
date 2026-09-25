package tenterexample.negative

import io.archinaut.tenter.view.Columns
import io.archinaut.tenter.view.View

// Deliberate negative fixture: this directory is excluded from ordinary source sets.
public fun rawColumn(raw: View): Columns.Child = Columns.Child(10, raw)
