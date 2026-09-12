package lgka

/**
 * Typed failure of the verified data layer. Every parser throws this (never
 * a bare IllegalStateException) so the UI can distinguish "the school
 * changed its format" from generic I/O problems.
 */
class LgkaParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
