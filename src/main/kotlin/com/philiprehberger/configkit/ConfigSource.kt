package com.philiprehberger.configkit

import java.io.File
import java.util.Properties

/**
 * A source of configuration key-value pairs.
 *
 * Implementations load configuration from various backends
 * (environment variables, files, maps, etc.).
 */
interface ConfigSource {
    /**
     * Loads all configuration entries from this source.
     *
     * @return a map of configuration keys to their string values
     */
    fun load(): Map<String, String>
}

/**
 * Reads configuration from environment variables.
 *
 * Optionally filters by a prefix and converts keys from UPPER_SNAKE_CASE
 * to dot.case notation.
 *
 * For example, with prefix "APP": `APP_DB_HOST` becomes `db.host`.
 * Without a prefix: `DB_HOST` becomes `db.host`.
 *
 * @property prefix optional prefix to filter and strip from environment variable names
 */
class EnvVarSource(private val prefix: String? = null) : ConfigSource {
    override fun load(): Map<String, String> {
        val env = System.getenv()
        val result = mutableMapOf<String, String>()

        for ((key, value) in env) {
            if (prefix != null) {
                val prefixUpper = prefix.uppercase() + "_"
                if (key.startsWith(prefixUpper)) {
                    val stripped = key.removePrefix(prefixUpper)
                    result[toDotCase(stripped)] = value
                }
            } else {
                result[toDotCase(key)] = value
            }
        }

        return result
    }

    private fun toDotCase(key: String): String {
        return key.lowercase().replace('_', '.')
    }
}

/**
 * A configuration source backed by a pre-built map.
 *
 * @property map the configuration entries
 */
class MapSource(private val map: Map<String, String>) : ConfigSource {
    override fun load(): Map<String, String> = map
}

/**
 * Reads configuration from a Java `.properties` file.
 *
 * @property path the file path to the properties file
 */
class PropertiesFileSource(private val path: String) : ConfigSource {
    override fun load(): Map<String, String> {
        val props = Properties()
        File(path).inputStream().use { props.load(it) }
        return props.entries.associate { (k, v) -> k.toString() to v.toString() }
    }
}
