package tenter.text

public object TextWrap {

    /**
     * The wrap engine, expressed as index ranges into [text] so a caller carrying per-index
     * decoration (styling, e.g.) slices its own representation at exactly the boundaries this
     * chose, instead of re-implementing the break rules. Each element of the result is one
     * output row, given as the inclusive index ranges of the words it holds, in order;
     * consecutive words on a row were separated by exactly one space in [text], so a renderer
     * joins them with one space. A word wider than the row capacity is hard-split by codepoint,
     * so a range never ends inside a surrogate pair.
     */
    public fun <T> wrapBy(
        text: String,
        firstWidth: Int,
        continuationWidth: Int = firstWidth,
        row: (List<IntRange>) -> T,
    ): List<T> {
        val firstCap = firstWidth.coerceAtLeast(1)
        val contCap = continuationWidth.coerceAtLeast(1)

        val rows = mutableListOf<T>()
        var current = mutableListOf<IntRange>()
        var currentWidth = 0
        var capacity = firstCap

        fun flush() {
            if (current.isNotEmpty()) {
                rows += row(current)
                current = mutableListOf()
                currentWidth = 0
            }
        }

        forEachWord(text) { start, end ->
            val sepWidth = if (current.isEmpty()) 0 else 1
            val wordWidth = CellWidth.of(text, start, end)
            val needed = sepWidth + wordWidth
            if (currentWidth + needed <= capacity) {
                current += start until end
                currentWidth += needed
            } else {
                flush()
                capacity = contCap
                if (wordWidth <= capacity) {
                    current += start until end
                    currentWidth = wordWidth
                } else {
                    var i = start
                    var chunkStart = start
                    var chunkWidth = 0
                    while (i < end) {
                        val cp = text.codePointAt(i)
                        val cpLen = Character.charCount(cp)
                        val w = CellWidth.of(cp)
                        if (chunkWidth + w > capacity) {
                            rows += row(listOf(chunkStart until i))
                            chunkStart = i
                            chunkWidth = 0
                        }
                        chunkWidth += w
                        i += cpLen
                    }
                    current += chunkStart until end
                    currentWidth = chunkWidth
                }
            }
        }
        flush()
        return rows
    }

    public fun wrap(text: String, firstWidth: Int, continuationWidth: Int = firstWidth): List<String> =
        wrapBy(text, firstWidth, continuationWidth) { ranges ->
            ranges.joinToString(" ") { text.substring(it.first, it.last + 1) }
        }

    /** Maximal runs of non-space characters, as `[start, end)` pairs — what `split(' ')` tokenized. */
    private inline fun forEachWord(text: String, action: (start: Int, end: Int) -> Unit) {
        var i = 0
        while (i < text.length) {
            while (i < text.length && text[i] == ' ') i++
            if (i >= text.length) break
            val start = i
            while (i < text.length && text[i] != ' ') i++
            action(start, i)
        }
    }
}
