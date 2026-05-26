package battletech.tui.screen

internal object TextWrap {

    fun wrap(text: String, firstWidth: Int, continuationWidth: Int = firstWidth): List<String> {
        val firstCap = firstWidth.coerceAtLeast(1)
        val contCap = continuationWidth.coerceAtLeast(1)
        val words = text.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        var current = StringBuilder()
        var currentWidth = 0
        var capacity = firstCap

        for (word in words) {
            val sepWidth = if (current.isEmpty()) 0 else 1
            val wordWidth = CellWidth.of(word)
            val needed = sepWidth + wordWidth
            if (currentWidth + needed <= capacity) {
                if (sepWidth > 0) current.append(' ')
                current.append(word)
                currentWidth += needed
            } else {
                if (current.isNotEmpty()) {
                    lines += current.toString()
                    current = StringBuilder()
                    currentWidth = 0
                }
                capacity = contCap
                if (wordWidth <= capacity) {
                    current.append(word)
                    currentWidth = wordWidth
                } else {
                    var i = 0
                    var chunkStart = 0
                    var chunkWidth = 0
                    while (i < word.length) {
                        val cp = word.codePointAt(i)
                        val cpLen = Character.charCount(cp)
                        val w = CellWidth.of(cp)
                        if (chunkWidth + w > capacity) {
                            lines += word.substring(chunkStart, i)
                            chunkStart = i
                            chunkWidth = 0
                        }
                        chunkWidth += w
                        i += cpLen
                    }
                    current.append(word.substring(chunkStart))
                    currentWidth = chunkWidth
                }
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }
}
