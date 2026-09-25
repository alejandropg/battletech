package io.archinaut.tenter.view

/**
 * Stacks prepared [ContentView] children without painting them to discover their sizes. The
 * intrinsic layout includes each child's logical height and gutters between children only.
 * Wrap raw painting in [fixedContent] before inserting it into a stack.
 */
public class Stack(
    private val children: List<ContentView>,
    private val gutter: Int = 1,
) : ContentView {

    init {
        require(gutter >= 0) { "gutter must not be negative: $gutter" }
    }

    public override fun layout(availableWidth: Int): ContentLayout {
        require(availableWidth >= 0) { "available width must not be negative: $availableWidth" }
        val prepared = children.map { child ->
            child.layout(availableWidth)
        }
        val width = prepared.maxOfOrNull { it.width } ?: 0
        val height = prepared.foldIndexed(0) { index, total, child ->
            val withChild = checkedAdd(total, child.height, "stack height")
            if (index < prepared.lastIndex) checkedAdd(withChild, gutter, "stack height") else withChild
        }
        return contentLayout(width, height) {
            var row = 0
            prepared.forEachIndexed { index, child ->
                place(0, row, child)
                row = checkedAdd(row, child.height, "stack placement")
                if (index < prepared.lastIndex) row = checkedAdd(row, gutter, "stack placement")
            }
        }
    }
}
