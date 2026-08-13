package tenter.panel

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A transient status-bar message, auto-dismissed after [duration]. */
public data class FlashMessage(val text: String, val duration: Duration = 3.seconds)
