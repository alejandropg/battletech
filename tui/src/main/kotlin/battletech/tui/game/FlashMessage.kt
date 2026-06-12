package battletech.tui.game

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public data class FlashMessage(val text: String, val duration: Duration = 3.seconds)
