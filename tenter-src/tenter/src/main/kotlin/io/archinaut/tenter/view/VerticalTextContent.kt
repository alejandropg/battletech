package io.archinaut.tenter.view

import io.archinaut.tenter.screen.Cell
import io.archinaut.tenter.palette.ChromeRole
import io.archinaut.tenter.text.textClusters

internal fun verticalTextContent(title: String, style: Cell.Style = Cell.Style(ChromeRole.TEXT_PRIMARY)): ContentView =
    contentView { cursor ->
        for (cluster in textClusters(title)) {
            if (cluster.width == 0) continue
            cursor.write(cursor.width / 2, cluster.drawableText, style)
            cursor.newLine()
        }
    }
