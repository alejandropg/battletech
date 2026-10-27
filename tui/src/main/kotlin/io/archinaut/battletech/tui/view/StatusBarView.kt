package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.model.TurnPhase
import io.archinaut.battletech.tactical.unit.VisibleUnit
import io.archinaut.battletech.tui.game.displayName
import io.archinaut.tenter.color.StandardRole
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Line
import io.archinaut.tenter.screen.Padding
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.widget.Block
import io.archinaut.tenter.widget.Paragraph
import io.archinaut.tenter.widget.Widget

internal class StatusBarView(
    private val phase: TurnPhase,
    private val prompt: String,
    private val activePlayer: PlayerId? = null,
    private val actionUnit: VisibleUnit? = null,
) : Widget {

    override fun render(canvas: Canvas) {
        val block = Block.bordered(padding = STATUS_BAR_PADDING)
        block.render(canvas)
        renderContent(block.inner(canvas))
    }

    private fun renderContent(canvas: Canvas) {
        val message = messageText()
        val compact = paddedContentWidth(message) > canvas.width
        val leadingPadding = if (compact) "" else LEADING_PADDING
        val phaseText = if (compact) phaseLabel(phase) else centeredPhaseLabel(phase)
        val separator = if (compact) COMPACT_SEPARATOR else PADDED_SEPARATOR
        val playerWidth = if (compact) activePlayer?.let { CellWidth.of(it.displayName) } ?: 0 else PLAYER_WIDTH

        val player = if (activePlayer != null) {
            Line(activePlayer.displayName, Style(playerColor(activePlayer))) +
                Line(" ".repeat(playerWidth - CellWidth.of(activePlayer.displayName)))
        } else {
            Line(" ".repeat(playerWidth))
        }
        val line = Line(leadingPadding) +
            Line(phaseText, ACCENT_STYLE) +
            Line(separator, TEXT_PRIMARY_STYLE) +
            player +
            Line(separator, TEXT_PRIMARY_STYLE) +
            Line(message, TEXT_PRIMARY_STYLE)

        // The line stops short of the help hint, so an over-long message ellipsizes before it.
        val lineWidth = (HelpHint.column(canvas) - MESSAGE_HELP_GAP).coerceAtLeast(0)
        Paragraph(listOf(line)).render(canvas.region(Rect(0, 0, lineWidth, 1)))
        HelpHint.draw(canvas, 0)
    }

    private fun paddedContentWidth(message: String): Int =
        CellWidth.of(LEADING_PADDING) +
            PHASE_WIDTH +
            CellWidth.of(PADDED_SEPARATOR) +
            PLAYER_WIDTH +
            CellWidth.of(PADDED_SEPARATOR) +
            CellWidth.of(message) +
            MESSAGE_HELP_GAP +
            HelpHint.WIDTH

    private fun messageText(): String =
        actionUnit?.let { "${UnitLabel.of(it)}$UNIT_MESSAGE_SEPARATOR$prompt" } ?: prompt

    private companion object {
        private const val LEADING_PADDING: String = "  "
        private const val PADDED_SEPARATOR: String = "   |   "
        private const val COMPACT_SEPARATOR: String = " | "
        private const val UNIT_MESSAGE_SEPARATOR: String = " ┆ "
        private const val MESSAGE_HELP_GAP: Int = 1
        private val ACCENT_STYLE = Style(StandardRole.ACCENT)
        private val TEXT_PRIMARY_STYLE = Style(StandardRole.TEXT_PRIMARY)
        private val PHASE_WIDTH = TurnPhase.entries.maxOf { CellWidth.of(phaseLabel(it)) }
        private val PLAYER_WIDTH = PlayerId.entries.maxOf { CellWidth.of(it.displayName) }

        /**
         * The status bar is only [Workspace.STATUS_BAR_HEIGHT] = 3 rows tall: border alone
         * takes 2 and the phase, player, prompt, and help hint share the remaining row.
         */
        private val STATUS_BAR_PADDING = Padding(left = 1, right = 1)

        private fun phaseLabel(phase: TurnPhase): String = phase.name.replace('_', ' ')

        private fun centeredPhaseLabel(phase: TurnPhase): String {
            val label = phaseLabel(phase)
            val padding = PHASE_WIDTH - CellWidth.of(label)
            val leftPadding = padding / 2
            return " ".repeat(leftPadding) + label + " ".repeat(padding - leftPadding)
        }
    }
}
