package tenterexample

internal const val EXAMPLE_ROW_COUNT: Int = 600

internal data class ExampleState(
    internal val selectedRow: Int = 0,
    internal val checkedRows: Set<Int> = emptySet(),
)
