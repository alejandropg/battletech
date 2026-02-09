package battletech.tactical.action

public sealed interface ActionOption {
    val id: ActionId
    val name: String
}
