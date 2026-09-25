package tenterexample

import tenter.view.View

internal data class AnimationProbe(
    val firstFrame: View,
    val secondFrame: View,
    val completedFrameCount: Int,
)
