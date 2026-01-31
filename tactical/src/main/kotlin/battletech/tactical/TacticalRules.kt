package battletech.tactical

class TacticalRules {
    fun calculateToHit(gunnerySkill: Int, range: Int): Int {
        return gunnerySkill + range
    }
}
