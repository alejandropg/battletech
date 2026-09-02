package battletech.tui.view

import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.unit.VisibleUnit
import battletech.tui.game.displayName
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.screen.Insets
import tenter.screen.StyledText
import tenter.screen.styled
import tenter.text.CellWidth
import tenter.text.TextTruncation
import tenter.view.Bordered
import tenter.view.TextCursor
import tenter.view.View

internal class StatusBarView(
    private val phase: TurnPhase,
    private val prompt: String,
    private val activePlayer: PlayerId? = null,
    private val actionUnit: VisibleUnit? = null,
) : View {

    override fun draw(canvas: Canvas) {
        Bordered(gutters = STATUS_BAR_PADDING, content = Content()).draw(canvas)
    }

    private inner class Content : View {
        override fun draw(canvas: Canvas) {
            val message = messageText()
            val compact = paddedContentWidth(message) > canvas.width
            val leadingPadding = if (compact) "" else LEADING_PADDING
            val phaseText = if (compact) phaseLabel(phase) else centeredPhaseLabel(phase)
            val separator = if (compact) COMPACT_SEPARATOR else PADDED_SEPARATOR
            val playerWidth = if (compact) activePlayer?.let { CellWidth.of(it.displayName) } ?: 0 else PLAYER_WIDTH

            val prefix = styled {
                append(leadingPadding)
                append(phaseText, ACCENT_STYLE)
                append(separator, TEXT_PRIMARY_STYLE)
                if (activePlayer != null) {
                    append(activePlayer.displayName, Cell.Style(playerColor(activePlayer)))
                    append(" ".repeat(playerWidth - CellWidth.of(activePlayer.displayName)))
                } else {
                    append(" ".repeat(playerWidth))
                }
                append(separator, TEXT_PRIMARY_STYLE)
            }

            val helpColumn = HelpHint.column(canvas)
            val messageWidth = (helpColumn - MESSAGE_HELP_GAP - prefix.width).coerceAtLeast(0)
            TextCursor(canvas).writeLine(prefix + StyledText.of(TextTruncation.ellipsize(message, messageWidth), TEXT_PRIMARY_STYLE))

            HelpHint.draw(canvas, 0)
        }
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
        private val ACCENT_STYLE = Cell.Style(ChromeRole.ACCENT)
        private val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
        private val PHASE_WIDTH = TurnPhase.entries.maxOf { phaseLabel(it).length }
        private val PLAYER_WIDTH = PlayerId.entries.maxOf { it.displayName.length }

        /**
         * The status bar is only [Workspace.STATUS_BAR_HEIGHT] = 3 rows tall: border alone
         * takes 2 and the phase, player, prompt, and help hint share the remaining row.
         */
        private val STATUS_BAR_PADDING = Insets(left = 1, right = 1)

        private fun phaseLabel(phase: TurnPhase): String = phase.name.replace('_', ' ')

        private fun centeredPhaseLabel(phase: TurnPhase): String {
            val label = phaseLabel(phase)
            val padding = PHASE_WIDTH - label.length
            val leftPadding = padding / 2
            return " ".repeat(leftPadding) + label + " ".repeat(padding - leftPadding)
        }
    }
}
