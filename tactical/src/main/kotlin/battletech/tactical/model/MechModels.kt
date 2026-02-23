package battletech.tactical.model

public object MechModels {
    private val registry: Map<String, MechModel> = listOf(
        MechModel(
            variant = "AS7-D",
            name = "Atlas AS7-D",
            walkingMP = 3,
            runningMP = 5,
            heatSinkCapacity = 20,
            weapons = listOf(Weapons::mediumLaser, Weapons::ac20),
        ),
        MechModel(
            variant = "HBK-4G",
            name = "Hunchback HBK-4G",
            walkingMP = 4,
            runningMP = 6,
            weapons = listOf(Weapons::ac20),
        ),
        MechModel(
            variant = "WVR-6R",
            name = "Wolverine WVR-6R",
            walkingMP = 5,
            runningMP = 8,
            jumpMP = 5,
            weapons = listOf(Weapons::srm6, Weapons::mediumLaser),
        ),
    ).associateBy { it.variant }

    public operator fun get(variant: String): MechModel =
        registry[variant] ?: error("Unknown mech variant: $variant")
}
