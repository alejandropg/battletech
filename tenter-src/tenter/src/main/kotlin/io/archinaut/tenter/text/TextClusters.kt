package io.archinaut.tenter.text

import java.util.regex.Pattern

/** One complete JDK-grapheme cluster and the metrics shared by text consumers. */
internal data class TextCluster(
    internal val startIndex: Int,
    internal val endIndex: Int,
    internal val sourceText: String,
    internal val drawableText: String,
    internal val width: Int,
    internal val firstSpacingBaseIndex: Int?,
)

private val GRAPHEME_PATTERN: Pattern = Pattern.compile("\\X")

/**
 * Splits [text] at JDK grapheme boundaries. The source range is retained for callers that must
 * slice the original string, while [TextCluster.drawableText] is sanitized for terminal paint.
 */
internal fun textClusters(text: String): Sequence<TextCluster> = sequence {
    val matcher = GRAPHEME_PATTERN.matcher(text)
    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()
        val source = text.substring(start, end)
        var index = 0
        var width = 0
        var firstSpacingBaseIndex: Int? = null
        val drawable = StringBuilder(source.length)

        while (index < source.length) {
            val codePoint = source.codePointAt(index)
            val codePointLength = Character.charCount(codePoint)
            val validCodePoint = if (codePoint in SURROGATE_RANGE) REPLACEMENT_CODE_POINT else codePoint
            if (!isTerminalControl(validCodePoint)) {
                drawable.appendCodePoint(validCodePoint)
                width = maxOf(width, CellWidth.of(validCodePoint))
                if (firstSpacingBaseIndex == null && isSpacingBase(validCodePoint)) {
                    firstSpacingBaseIndex = start + index
                }
            }
            index += codePointLength
        }

        yield(
            TextCluster(
                startIndex = start,
                endIndex = end,
                sourceText = source,
                drawableText = drawable.toString(),
                width = if (firstSpacingBaseIndex == null) 0 else width.coerceIn(0, 2),
                firstSpacingBaseIndex = firstSpacingBaseIndex,
            ),
        )
    }
}

private fun isSpacingBase(codePoint: Int): Boolean =
    !isTerminalControl(codePoint) &&
        Character.getType(codePoint).toByte() !in NON_SPACING_TYPES &&
        CellWidth.of(codePoint) > 0

private fun isTerminalControl(codePoint: Int): Boolean = codePoint < 0x20 || codePoint in 0x7F..0x9F

private val SURROGATE_RANGE: IntRange = 0xD800..0xDFFF
private val NON_SPACING_TYPES: Set<Byte> = setOf(
    Character.NON_SPACING_MARK.toByte(),
    Character.COMBINING_SPACING_MARK.toByte(),
    Character.ENCLOSING_MARK.toByte(),
    Character.FORMAT.toByte(),
)
private const val REPLACEMENT_CODE_POINT: Int = 0xFFFD
