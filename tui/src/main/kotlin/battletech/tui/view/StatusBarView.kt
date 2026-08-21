package battletech.tui.view

import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.unit.VisibleUnit
import battletech.tui.game.displayName
import tenter.input.KeyGlyph
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.screen.Insets
import tenter.text.CellWidth
import tenter.view.Bordered
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
            var column = LEADING_PADDING.length

            canvas.writeString(column, 0, centeredPhaseLabel(phase), ACCENT_STYLE)
            column += PHASE_WIDTH

            canvas.writeString(column, 0, SEPARATOR, TEXT_PRIMARY_STYLE)
            column += SEPARATOR.length

            activePlayer?.let {
                canvas.writeString(column, 0, it.displayName, Cell.Style(playerColor(it)))
            }
            column += PLAYER_WIDTH

            canvas.writeString(column, 0, SEPARATOR, TEXT_PRIMARY_STYLE)
            column += SEPARATOR.length

            val helpColumn = canvas.width - HELP_WIDTH
            val messageWidth = (helpColumn - MESSAGE_HELP_GAP - column).coerceAtLeast(0)
            canvas.region(column, 0, messageWidth, 1)
                .writeString(0, 0, messageText(), TEXT_PRIMARY_STYLE)

            if (helpColumn >= 0) {
                canvas.writeString(helpColumn, 0, HELP_LABEL, TEXT_PRIMARY_STYLE)
            }
        }
    }

    private fun messageText(): String {
        val message = normalizedPrompt()
        return actionUnit?.let { "${UnitLabel.of(it)}$UNIT_MESSAGE_SEPARATOR$message" } ?: message
    }

    private fun normalizedPrompt(): String {
        val playerPrefix = PlayerId.entries
            .map { "${it.displayName}:" }
            .firstOrNull(prompt::startsWith)
            ?: return prompt
        return prompt.removePrefix(playerPrefix)
            .trimStart()
            .replaceFirstChar { it.titlecase() }
    }

    private companion object {
        private const val LEADING_PADDING: String = "  "
        private const val SEPARATOR: String = "   |   "
        private const val UNIT_MESSAGE_SEPARATOR: String = " ┆ "
        private const val MESSAGE_HELP_GAP: Int = 1
        private const val HELP_LABEL: String = "${KeyGlyph.ALT}h : help"

        private val ACCENT_STYLE = Cell.Style(ChromeRole.ACCENT)
        private val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
        private val PHASE_WIDTH = TurnPhase.entries.maxOf { phaseLabel(it).length }
        private val PLAYER_WIDTH = PlayerId.entries.maxOf { it.displayName.length }
        private val HELP_WIDTH = CellWidth.of(HELP_LABEL)

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
