package tenter.text

public object CellWidth {

    public fun of(codePoint: Int): Int = when {
        codePoint < 0x20 -> 0
        codePoint in 0x7F..0x9F -> 0
        isZeroWidth(codePoint) -> 0
        isWide(codePoint) -> 2
        else -> 1
    }

    public fun of(text: String): Int = of(text, 0, text.length)

    /** Display width of the `[startIndex, endIndex)` code-unit slice of [text]. */
    public fun of(text: String, startIndex: Int, endIndex: Int): Int {
        var total = 0
        var i = startIndex
        while (i < endIndex) {
            val cp = text.codePointAt(i)
            total += of(cp)
            i += Character.charCount(cp)
        }
        return total
    }

    private fun isZeroWidth(cp: Int): Boolean =
        when (Character.getType(cp).toByte()) {
            Character.NON_SPACING_MARK,
            Character.ENCLOSING_MARK,
            Character.FORMAT -> true

            else -> false
        }

    private fun isWide(cp: Int): Boolean = when (cp) {
        in 0x1100..0x115F -> true
        in 0x2E80..0x303E -> true
        in 0x3041..0x33FF -> true
        in 0x3400..0x4DBF -> true
        in 0x4E00..0x9FFF -> true
        in 0xA000..0xA4CF -> true
        in 0xAC00..0xD7A3 -> true
        in 0xF900..0xFAFF -> true
        in 0xFE30..0xFE4F -> true
        in 0xFF00..0xFF60 -> true
        in 0xFFE0..0xFFE6 -> true
        in 0x1F300..0x1F64F -> true
        in 0x1F900..0x1F9FF -> true
        in 0x20000..0x2FFFD -> true
        in 0x30000..0x3FFFD -> true
        // Nerd-Font PUA (BMP U+E000..U+F8FF, supplementary U+F0000..U+FFFFD)
        // is intentionally treated as width 1: most terminals render these glyphs
        // in a single cell.
        else -> false
    }
}
