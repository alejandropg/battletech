package battletech.tui.game.phase

import battletech.tactical.action.ActionQueryService
import battletech.tactical.dice.DiceRoller
import battletech.tactical.dice.RandomDiceRoller

public class PhaseServices(
    public val actionQueryService: ActionQueryService,
    public val roller: DiceRoller = RandomDiceRoller(),
)
