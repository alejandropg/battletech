package tenter.view

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

/**
 * Wraps a raw view whose complete dimensions are known without measuring it. Retains [view],
 * without painting or copying it during layout; each subsequent paint invokes it once.
 * The caller must capture stable frame data in the view if repeatable painting is required.
 */
public fun fixedContent(width: Int, height: Int, view: View): ContentView {
    require(width >= 0) { "content width must not be negative: $width" }
    require(height >= 0) { "content height must not be negative: $height" }
    return object : ContentView {
        override fun layout(availableWidth: Int): ContentLayout = ContentLayout.raw(width, height, view)
    }
}
