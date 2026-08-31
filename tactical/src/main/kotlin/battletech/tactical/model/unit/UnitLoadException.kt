package battletech.tactical.model.unit

/** Raised when a JSON unit-collection file cannot be read, parsed, or assembled into a valid roster. */
public class UnitLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
