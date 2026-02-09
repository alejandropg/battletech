package battletech.tactical

public class TacticalRules {
    public fun calculateToHit(gunnerySkill: Int, range: Int): Int {
        return gunnerySkill + range
    }
}
