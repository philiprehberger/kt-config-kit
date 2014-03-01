package com.philiprehberger.configkit

/**
 * Layered configuration that merges values from multiple [ConfigSource] instances.
 *
 * Sources are applied in order: later sources override values from earlier ones.
 * Provides type-safe accessors for common types (String, Int, Boolean, List).
 *
 * @property sources the ordered list of configuration sources
 */
class Config(private val sources: List<ConfigSource>) {
    @PublishedApi internal val values: Map<String, String> by lazy {
        val merged = mutableMapOf<String, String>()
        for (source in sources) {
            merged.putAll(source.load())
        }
        merged
    }

    /**
     * Gets the value for the given key, throwing if not found.
     *
     * Supports the following types via reified generics:
     * - [String]
     * - [Int]
     * - [Long]
     * - [Boolean]
     * - [Double]
     *
     * @param key the configuration key
     * @return the typed value
     * @throws IllegalStateException if the key is not found
     * @throws IllegalArgumentException if the value cannot be converted to the requested type
     */
    inline fun <reified T> require(key: String): T {
        val raw = values[key] ?: throw IllegalStateException("Required config key '$key' not found")
        return convert(raw)
    }

    /**
     * Gets the value for the given key, returning [default] if not found.
     *
     * @param key the configuration key
     * @param default the fallback value
     * @return the typed value, or the default
     */
    inline fun <reified T> get(key: String, default: T): T {
        val raw = values[key] ?: return default
        return try {
            convert(raw)
        } catch (_: Exception) {
            default
        }
    }

    /**
     * Gets the raw string value for the given key.
     *
     * @param key the configuration key
     * @return the string value, or null if not found
     */
    fun getString(key: String): String? = values[key]

    /**
     * Gets the value as an integer.
     *
     * @param key the configuration key
     * @return the integer value, or null if not found or not parseable
     */
    fun getInt(key: String): Int? = values[key]?.toIntOrNull()

    /**
     * Gets the value as a boolean.
     *
     * Recognized values: "true", "1", "yes" (case-insensitive) for true; everything else for false.
     *
     * @param key the configuration key
     * @return the boolean value, or null if not found
     */
    fun getBoolean(key: String): Boolean? {
        val raw = values[key] ?: return null
        return raw.lowercase() in setOf("true", "1", "yes")
    }

    /**
     * Gets the value as a list of strings, split by the given delimiter.
     *
     * @param key the configuration key
     * @param delimiter the string to split on (default: ",")
     * @return the list of trimmed strings, or null if the key is not found
     */
    fun getList(key: String, delimiter: String = ","): List<String>? {
        val raw = values[key] ?: return null
        return raw.split(delimiter).map { it.trim() }
    }

    @PublishedApi
    internal inline fun <reified T> convert(raw: String): T {
        @Suppress("UNCHECKED_CAST")
        return when (T::class) {
            String::class -> raw as T
            Int::class -> raw.toInt() as T
            Long::class -> raw.toLong() as T
            Boolean::class -> (raw.lowercase() in setOf("true", "1", "yes")) as T
            Double::class -> raw.toDouble() as T
            else -> throw IllegalArgumentException("Unsupported type: ${T::class}")
        }
    }
}
