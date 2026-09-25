package tenter.view

/**
 * Adds two composition dimensions, failing with a named error instead of wrapping into a negative
 * size. Every prepared composition ([Stack], [Columns], [Padded.prepared], [Bordered.prepared])
 * derives its dimensions this way, so an overflow reports which composition produced it rather
 * than surfacing later as a `ContentLayout` size assertion.
 */
internal fun checkedAdd(left: Int, right: Int, description: String): Int =
    try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        error("$description overflowed")
    }
