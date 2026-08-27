package tenter.screen

import tenter.text.CellWidth
import tenter.text.TextTruncation
import tenter.text.TextWrap

/**
 * Text whose styling varies along its length: a sequence of same-styled [Span]s that measures,
 * wraps, truncates, and paints as one unit.
 *
 * It exists so a caller wanting two colors on one line stops hand-computing columns. The old
 * shape — a `writeString(column, …)` per fragment with the caller summing widths — collapses
 * the moment the line also has to wrap or ellipsize, because the caller then owns break
 * decisions the wrap engine already knows how to make. Build one with [styled], hand it to
 * `tenter.view.TextCursor.writeLine`, never compute a column.
 *
 * Spans are canonical: empty spans are dropped and adjacent same-styled spans merged, so two
 * instances that paint identically are [equals] — which is what makes them usable in
 * assertions.
 *
 * Indices used internally are code-unit indices into [plain]; slices never split a surrogate
 * pair because every boundary comes from [TextWrap] or [TextTruncation], both of which advance
 * by codepoint.
 */
public class StyledText private constructor(public val spans: List<Span>) {

    public data class Span(val text: String, val style: Cell.Style)

    /** The unstyled projection — what a plain-text consumer (a console log, an assertion) prints. */
    public val plain: String by lazy(LazyThreadSafetyMode.NONE) { spans.joinToString("") { it.text } }

    /** Display width in terminal cells. */
    public val width: Int get() = CellWidth.of(plain)

    public val isEmpty: Boolean get() = spans.isEmpty()

    public operator fun plus(other: StyledText): StyledText =
        styled { append(this@StyledText); append(other) }

    /** Span-aware [TextWrap.wrap]: identical break decisions, each row's styling sliced to match. */
    public fun wrap(firstWidth: Int, continuationWidth: Int = firstWidth): List<StyledText> =
        TextWrap.wrapBy(plain, firstWidth, continuationWidth) { ranges ->
            styled {
                ranges.forEachIndexed { i, range ->
                    // The separating space is the source's own, so it keeps the source's style —
                    // which matters the moment a span carries a background.
                    if (i > 0) append(slice(range.first - 1, range.first))
                    append(slice(range.first, range.last + 1))
                }
            }
        }

    /** Span-aware [TextTruncation.ellipsize]; the ellipsis inherits the style it interrupts. */
    public fun ellipsize(maxWidth: Int): StyledText {
        val available = maxWidth.coerceAtLeast(0)
        if (width <= available) return this
        if (available == 0) return EMPTY
        val keep = TextTruncation.prefixLengthWithin(plain, available - CellWidth.of(TextTruncation.ELLIPSIS))
        return styled {
            append(slice(0, keep))
            append(TextTruncation.ELLIPSIS, styleAt(keep))
        }
    }

    /** The `[startIndex, endIndex)` slice of [plain], carrying its styling. */
    private fun slice(startIndex: Int, endIndex: Int): StyledText = styled {
        var offset = 0
        for (span in spans) {
            val spanEnd = offset + span.text.length
            val from = maxOf(startIndex, offset)
            val to = minOf(endIndex, spanEnd)
            if (from < to) append(span.text.substring(from - offset, to - offset), span.style)
            offset = spanEnd
        }
    }

    /** The style covering code-unit [index]; clamps to the last span so an ellipsis at the end works. */
    private fun styleAt(index: Int): Cell.Style {
        var offset = 0
        for (span in spans) {
            offset += span.text.length
            if (index < offset) return span.style
        }
        return spans.lastOrNull()?.style ?: Cell.Style.DEFAULT
    }

    override fun equals(other: Any?): Boolean = other is StyledText && spans == other.spans
    override fun hashCode(): Int = spans.hashCode()
    override fun toString(): String = "StyledText($spans)"

    public class Builder internal constructor() {
        private val spans = mutableListOf<Span>()

        public fun append(text: String, style: Cell.Style = Cell.Style.DEFAULT) {
            if (text.isEmpty()) return
            val last = spans.lastOrNull()
            if (last != null && last.style == style) {
                spans[spans.lastIndex] = last.copy(text = last.text + text)
            } else {
                spans += Span(text, style)
            }
        }

        /** The overwhelmingly common case: a foreground color and nothing else. */
        public fun append(text: String, fg: ColorRole): Unit = append(text, Cell.Style(fg = fg))

        public fun append(other: StyledText): Unit = other.spans.forEach { append(it.text, it.style) }

        internal fun build(): StyledText = StyledText(spans.toList())
    }

    public companion object {
        public val EMPTY: StyledText = StyledText(emptyList())

        public fun of(text: String, style: Cell.Style = Cell.Style.DEFAULT): StyledText =
            if (text.isEmpty()) EMPTY else StyledText(listOf(Span(text, style)))

        public fun of(text: String, fg: ColorRole): StyledText = of(text, Cell.Style(fg = fg))
    }
}

public fun styled(block: StyledText.Builder.() -> Unit): StyledText =
    StyledText.Builder().apply(block).build()

/** [joinToString] for styled fragments; the separator is unstyled. */
public fun Iterable<StyledText>.joinStyled(separator: String = ""): StyledText = styled {
    this@joinStyled.forEachIndexed { i, part ->
        if (i > 0) append(separator)
        append(part)
    }
}
