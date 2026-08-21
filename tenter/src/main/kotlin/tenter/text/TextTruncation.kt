package tenter.text

public object TextTruncation {

    private const val ELLIPSIS: String = "…"

    public fun ellipsize(text: String, maxWidth: Int): String {
        val availableWidth = maxWidth.coerceAtLeast(0)
        if (CellWidth.of(text) <= availableWidth) return text
        if (availableWidth == 0) return ""

        return truncateToWidth(text, availableWidth - CellWidth.of(ELLIPSIS)) + ELLIPSIS
    }

    private fun truncateToWidth(text: String, maxWidth: Int): String {
        var displayWidth = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val codePointWidth = CellWidth.of(codePoint)
            if (displayWidth + codePointWidth > maxWidth) break
            displayWidth += codePointWidth
            index += Character.charCount(codePoint)
        }
        return text.substring(0, index)
    }
}
