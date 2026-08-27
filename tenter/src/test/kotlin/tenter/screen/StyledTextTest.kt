package tenter.screen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.text.TextWrap

internal class StyledTextTest {

    @Test
    fun `builder merges adjacent same-styled spans`() {
        val text = styled {
            append("foo", ChromeRole.DANGER)
            append("bar", ChromeRole.DANGER)
        }

        assertEquals(listOf(StyledText.Span("foobar", Cell.Style(ChromeRole.DANGER))), text.spans)
    }

    @Test
    fun `builder drops empty appends`() {
        val text = styled {
            append("foo", ChromeRole.DANGER)
            append("", ChromeRole.SUCCESS)
            append("bar", ChromeRole.SUCCESS)
        }

        assertEquals(
            listOf(StyledText.Span("foo", Cell.Style(ChromeRole.DANGER)), StyledText.Span("bar", Cell.Style(ChromeRole.SUCCESS))),
            text.spans,
        )
    }

    @Test
    fun `two values that paint identically are equal`() {
        val a = styled { append("foo", ChromeRole.DANGER) }
        val b = styled {
            append("f", ChromeRole.DANGER)
            append("oo", ChromeRole.DANGER)
        }

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `plain concatenates every span's text`() {
        val text = styled {
            append("Grasshopper", ChromeRole.ACCENT)
            append(" destroyed")
        }

        assertEquals("Grasshopper destroyed", text.plain)
    }

    @Test
    fun `wrapping matches the flat wrap engine's line breaks`() {
        val text = styled {
            append("The quick brown", ChromeRole.ACCENT)
            append(" fox jumps over the lazy dog")
        }

        for (width in listOf(3, 5, 8, 12, 40)) {
            val expected = TextWrap.wrap(text.plain, width)
            val actual = text.wrap(width).map { it.plain }
            assertEquals(expected, actual, "width=$width")
        }
    }

    @Test
    fun `a span straddling a wrap point is sliced and each row keeps its style`() {
        val text = styled {
            append("Grasshopper", ChromeRole.ACCENT)
            append(" hits ", Cell.Style.DEFAULT)
            append("Atlas", ChromeRole.DANGER)
        }

        val rows = text.wrap(firstWidth = 11, continuationWidth = 10)

        assertEquals(listOf("Grasshopper", "hits Atlas"), rows.map { it.plain })
        assertEquals(listOf(StyledText.Span("Grasshopper", Cell.Style(ChromeRole.ACCENT))), rows[0].spans)
        assertEquals(
            listOf(
                StyledText.Span("hits ", Cell.Style.DEFAULT),
                StyledText.Span("Atlas", Cell.Style(ChromeRole.DANGER)),
            ),
            rows[1].spans,
        )
    }

    @Test
    fun `an over-long word inside one colored span hard-breaks and every fragment keeps the style`() {
        val text = styled { append("supercalifragilistic", ChromeRole.DANGER) }

        val rows = text.wrap(8)

        assertEquals(listOf("supercal", "ifragili", "stic"), rows.map { it.plain })
        rows.forEach { row ->
            assertEquals(listOf(Cell.Style(ChromeRole.DANGER)), row.spans.map { it.style }.distinct())
        }
    }

    @Test
    fun `the separating space between two differently-styled words keeps the source's style`() {
        val text = styled {
            append("foo", ChromeRole.DANGER)
            append(" ", ChromeRole.SUCCESS)
            append("bar", ChromeRole.ACCENT)
        }

        val row = text.wrap(20).single()

        assertEquals(
            listOf(
                StyledText.Span("foo", Cell.Style(ChromeRole.DANGER)),
                StyledText.Span(" ", Cell.Style(ChromeRole.SUCCESS)),
                StyledText.Span("bar", Cell.Style(ChromeRole.ACCENT)),
            ),
            row.spans,
        )
    }

    @Test
    fun `ellipsize keeps text unchanged when it fits`() {
        val text = styled { append("hello", ChromeRole.ACCENT) }

        assertEquals(text, text.ellipsize(5))
        assertEquals(text, text.ellipsize(8))
    }

    @Test
    fun `ellipsize cuts mid-span and styles the ellipsis with the interrupted span's style`() {
        val text = styled { append("hello!", ChromeRole.DANGER) }

        val truncated = text.ellipsize(5)

        assertEquals("hell…", truncated.plain)
        assertEquals(listOf(StyledText.Span("hell…", Cell.Style(ChromeRole.DANGER))), truncated.spans)
    }

    @Test
    fun `ellipsize keeps a whole leading span intact when the cut falls in a later span`() {
        val text = styled {
            append("Atlas", ChromeRole.DANGER)
            append(" destroyed")
        }

        val truncated = text.ellipsize(7)

        assertEquals("Atlas …", truncated.plain)
        assertEquals(
            listOf(StyledText.Span("Atlas", Cell.Style(ChromeRole.DANGER)), StyledText.Span(" …", Cell.Style.DEFAULT)),
            truncated.spans,
        )
    }

    @Test
    fun `ellipsize of zero width is EMPTY`() {
        val text = styled { append("hello", ChromeRole.ACCENT) }

        assertEquals(StyledText.EMPTY, text.ellipsize(0))
        assertTrue(text.ellipsize(0).isEmpty)
    }

    @Test
    fun `ellipsize of a negative width behaves as zero`() {
        val text = styled { append("hello", ChromeRole.ACCENT) }

        assertEquals(StyledText.EMPTY, text.ellipsize(-3))
    }

    @Test
    fun `joinStyled separates parts with an unstyled separator`() {
        val parts = listOf(
            styled { append("Grasshopper", ChromeRole.ACCENT) },
            styled { append("Atlas", ChromeRole.DANGER) },
        )

        val joined = parts.joinStyled(", ")

        assertEquals(
            listOf(
                StyledText.Span("Grasshopper", Cell.Style(ChromeRole.ACCENT)),
                StyledText.Span(", ", Cell.Style.DEFAULT),
                StyledText.Span("Atlas", Cell.Style(ChromeRole.DANGER)),
            ),
            joined.spans,
        )
    }

    @Test
    fun `of with an empty string yields EMPTY`() {
        assertEquals(StyledText.EMPTY, StyledText.of("", ChromeRole.ACCENT))
    }

    @Test
    fun `plus concatenates two styled texts`() {
        val a = styled { append("foo", ChromeRole.ACCENT) }
        val b = styled { append("bar", ChromeRole.DANGER) }

        val sum = a + b

        assertEquals("foobar", sum.plain)
        assertEquals(
            listOf(StyledText.Span("foo", Cell.Style(ChromeRole.ACCENT)), StyledText.Span("bar", Cell.Style(ChromeRole.DANGER))),
            sum.spans,
        )
    }
}
