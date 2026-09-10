package tenterexample.negative

import tenter.view.Stack
import tenter.view.View

// Deliberate negative fixture: this directory is excluded from ordinary source sets.
public fun rawStack(raw: View): Stack = Stack(listOf(raw))
