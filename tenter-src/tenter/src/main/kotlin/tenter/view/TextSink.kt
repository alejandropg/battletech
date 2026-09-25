package tenter.view

import tenter.screen.Cell

internal interface TextSink {
    val width: Int

    fun write(column: Int, row: Int, text: String, style: Cell.Style)

    fun reveal(x: Int, y: Int, width: Int, height: Int)

    fun place(layout: ContentLayout, x: Int, y: Int)
}
