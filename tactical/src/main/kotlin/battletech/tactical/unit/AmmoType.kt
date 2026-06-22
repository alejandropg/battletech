package battletech.tactical.unit

public enum class AmmoType(public val shotsPerTon: Int, public val damagePerShot: Int) {
    AC20(5, 20),
    AC5(20, 5),
    LRM5(24, 5),
    LRM10(12, 10),
    LRM20(6, 20),
    SRM2(50, 4),
    SRM6(15, 12),
    MG(200, 2),
}
