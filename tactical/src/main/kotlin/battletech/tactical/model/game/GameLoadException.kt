package battletech.tactical.model.game

/** Raised when a JSON game definition cannot be read, parsed, or built into a valid game. */
public class GameLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
