package tenter.text

public object TextTruncation {

    public const val ELLIPSIS: String = "…"

    public fun ellipsize(text: String, maxWidth: Int): String {
        val availableWidth = maxWidth.coerceAtLeast(0)
        if (CellWidth.of(text) <= availableWidth) return text
        if (availableWidth == 0) return ""

        val keep = prefixLengthWithin(text, availableWidth - CellWidth.of(ELLIPSIS))
        return text.substring(0, keep) + ELLIPSIS
    }

    /**
     * The exclusive code-unit index of the longest prefix of [text] fitting in [maxWidth]
     * display cells — never inside a grapheme cluster. Shared internally with
     * `tenter.screen.StyledText` so it does not re-derive the rule.
     */
    internal fun prefixLengthWithin(text: String, maxWidth: Int): Int {
        var displayWidth = 0
        var index = 0
        for (cluster in textClusters(text)) {
            if (displayWidth + cluster.width > maxWidth) break
            displayWidth += cluster.width
            index = cluster.endIndex
        }
        return index
    }
}
