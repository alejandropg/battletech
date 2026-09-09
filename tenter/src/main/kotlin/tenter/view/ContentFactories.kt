package tenter.view

import tenter.screen.Canvas

/** Builds a fixed-size prepared composition from private placement instructions. */
public fun contentLayout(
    width: Int,
    height: Int,
    revealPreference: RevealPreference = RevealPreference.LAST,
    block: ContentLayout.Builder.() -> Unit,
): ContentLayout {
    val builder = ContentLayout.Builder()
    builder.block()
    return ContentLayout(width, height, builder.snapshot(), revealPreference)
}

/**
 * Builds flowing content with a recording [TextCursor]. The callback runs once for each layout
 * request, and the returned layout is independent of later mutation of the callback's builder.
 */
public fun contentView(block: (TextCursor) -> Unit): ContentView = object : ContentView {
    override fun layout(availableWidth: Int): ContentLayout {
        require(availableWidth >= 0) { "available width must not be negative: $availableWidth" }
        val sink = RecordingTextSink(availableWidth)
        val cursor = TextCursor(availableWidth, sink)
        block(cursor)
        return ContentLayout(
            width = availableWidth,
            height = cursor.occupiedHeight,
            instructions = sink.snapshot(),
            revealPreference = RevealPreference.LAST,
        )
    }
}

/** Wraps a raw view whose complete content dimensions are known without measuring it. */
public fun fixedContent(width: Int, height: Int, view: View): ContentView {
    require(width >= 0) { "content width must not be negative: $width" }
    require(height >= 0) { "content height must not be negative: $height" }
    return object : ContentView {
        override fun layout(availableWidth: Int): ContentLayout = ContentLayout.raw(width, height, view)
    }
}

/** Temporary compatibility measurement for raw children of a prepared decorator. */
internal fun legacyContentLayout(width: Int, view: View): ContentLayout {
    val stream = Canvas.offscreen(width, LEGACY_MEASUREMENT_HEIGHT)
    view.draw(stream)
    return ContentLayout.raw(width, stream.contentHeight(), view)
}

private const val LEGACY_MEASUREMENT_HEIGHT: Int = 512
