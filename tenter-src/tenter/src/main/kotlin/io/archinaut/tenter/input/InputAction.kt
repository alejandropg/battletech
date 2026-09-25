package io.archinaut.tenter.input

/**
 * Something a key can do. The [id] is a stable, human-readable name — it exists so the HELP panel
 * and a caller's configuration can refer to an action without depending on its type. Implementations
 * live wherever the thing they act on lives: [PanAction]/[ScrollAction] here, everything that names
 * an application object (a panel, a game unit) in the application.
 */
public interface InputAction {
    public val id: String
}
