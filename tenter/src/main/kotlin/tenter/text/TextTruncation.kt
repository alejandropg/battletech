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
     * display cells — never inside a surrogate pair. Public so a decorated-text caller
     * (`tenter.screen.StyledText`) cuts where this cuts rather than re-deriving the rule.
     */
    public fun prefixLengthWithin(text: String, maxWidth: Int): Int {
        var displayWidth = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val codePointWidth = CellWidth.of(codePoint)
            if (displayWidth + codePointWidth > maxWidth) break
            displayWidth += codePointWidth
            index += Character.charCount(codePoint)
        }
        return index
    }
}
