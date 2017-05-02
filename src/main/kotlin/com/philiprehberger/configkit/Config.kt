package com.philiprehberger.configkit

/**
 * Layered configuration that merges values from multiple [ConfigSource] instances.
 *
 * Sources are applied in order: later sources override values from earlier ones.
 * Values containing `${key}` placeholders are interpolated by resolving the
 * referenced key within the same configuration. Circular references are detected
 * and cause an [IllegalStateException].
 *
 * @property sources the ordered list of configuration sources
 */
class Config(private val sources: List<ConfigSource>) {
    @PublishedApi internal val values: Map<String, String> by lazy {
        val merged = mutableMapOf<String, String>()
        for (source in sources) {
            merged.putAll(source.load())
        }
        interpolateAll(merged)
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
     * Gets the raw string value for the given key, returning [default] if not found.
     *
     * @param key the configuration key
     * @param default the fallback value
     * @return the string value, or the default
     */
    fun getStringOrDefault(key: String, default: String): String = values[key] ?: default

    /**
     * Gets the value as an integer.
     *
     * @param key the configuration key
     * @return the integer value, or null if not found or not parseable
     */
    fun getInt(key: String): Int? = values[key]?.toIntOrNull()

    /**
     * Gets the value as an integer, returning [default] if not found or not parseable.
     *
     * @param key the configuration key
     * @param default the fallback value
     * @return the integer value, or the default
     */
    fun getIntOrDefault(key: String, default: Int): Int = values[key]?.toIntOrNull() ?: default

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
     * Gets the value as a boolean, returning [default] if not found.
     *
     * @param key the configuration key
     * @param default the fallback value
     * @return the boolean value, or the default
     */
    fun getBooleanOrDefault(key: String, default: Boolean): Boolean {
        val raw = values[key] ?: return default
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

    /**
     * Gets the value as a list of strings, returning [default] if not found.
     *
     * @param key the configuration key
     * @param delimiter the string to split on (default: ",")
     * @param default the fallback value
     * @return the list of trimmed strings, or the default
     */
    fun getListOrDefault(key: String, delimiter: String = ",", default: List<String>): List<String> {
        val raw = values[key] ?: return default
        return raw.split(delimiter).map { it.trim() }
    }

    /**
     * Gets the value as an enum constant.
     *
     * The value is matched case-insensitively against enum constant names.
     *
     * @param T the enum type
     * @param key the configuration key
     * @return the enum value, or null if not found
     * @throws IllegalArgumentException if the value does not match any enum constant
     */
    inline fun <reified T : Enum<T>> getEnum(key: String): T? {
        val raw = values[key] ?: return null
        val upper = raw.uppercase()
        return enumValues<T>().find { it.name == upper }
            ?: throw IllegalArgumentException(
                "Invalid value '$raw' for enum ${T::class.simpleName}. " +
                "Valid values: ${enumValues<T>().joinToString { it.name }}"
            )
    }

    /**
     * Exports all resolved configuration as a flat map.
     *
     * @return an immutable map of all configuration keys and their interpolated values
     */
    fun toMap(): Map<String, String> = values.toMap()

    /**
     * Validates that all required keys are present.
     *
     * @param requiredKeys the keys that must exist
     * @throws IllegalStateException if any required keys are missing
     */
    fun validate(vararg requiredKeys: String) {
        val missing = requiredKeys.filter { it !in values }
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "Missing required configuration keys: ${missing.joinToString(", ")}"
            )
        }
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

    companion object {
        private val interpolationPattern = Regex("""\$\{([^}]+)}""")

        /**
         * Interpolates all `${key}` placeholders in the values map.
         * Detects circular references.
         */
        internal fun interpolateAll(raw: MutableMap<String, String>): Map<String, String> {
            val resolved = mutableMapOf<String, String>()
            val resolving = mutableSetOf<String>()

            fun resolve(key: String): String {
                resolved[key]?.let { return it }
                if (key in resolving) {
                    throw IllegalStateException("Circular reference detected for config key '$key'")
                }
                val value = raw[key] ?: return ""
                resolving.add(key)
                val result = interpolationPattern.replace(value) { match ->
                    val refKey = match.groupValues[1]
                    if (refKey !in raw) {
                        match.value // leave unresolved placeholders as-is
                    } else {
                        resolve(refKey)
                    }
                }
                resolving.remove(key)
                resolved[key] = result
                return result
            }

            for (key in raw.keys) {
                resolve(key)
            }
            return resolved
        }
    }
}
