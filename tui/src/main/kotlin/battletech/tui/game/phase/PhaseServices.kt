package battletech.tui.game.phase

import battletech.tactical.action.ActionQueryService
import kotlin.random.Random

public class PhaseServices(
    public val actionQueryService: ActionQueryService,
    public val random: Random = Random,
)
