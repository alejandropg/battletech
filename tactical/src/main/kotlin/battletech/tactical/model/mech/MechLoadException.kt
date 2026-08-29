package battletech.tactical.model.mech

/** Raised when a packaged or external mech-model collection cannot be loaded or validated. */
public class MechLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
