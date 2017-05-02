package com.philiprehberger.configkit

import java.io.File
import java.io.StringReader
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

/**
 * Reads configuration from a JSON file.
 *
 * Flattens nested objects using dot notation. For example:
 * ```json
 * { "db": { "host": "localhost", "port": "5432" } }
 * ```
 * produces `db.host=localhost` and `db.port=5432`.
 *
 * Only string values and nested objects are supported. Arrays, numbers,
 * booleans, and nulls are converted to their string representations.
 *
 * @property path the file path to the JSON file
 */
class JsonConfigSource(private val path: String) : ConfigSource {
    override fun load(): Map<String, String> {
        val content = File(path).readText().trim()
        val result = mutableMapOf<String, String>()
        if (content.startsWith("{")) {
            parseObject(content, "", result)
        }
        return result
    }

    internal companion object {
        /**
         * Parses a JSON object string and flattens it into dot-notation key-value pairs.
         */
        fun parseObject(json: String, prefix: String, result: MutableMap<String, String>) {
            val body = json.trim().removeSurrounding("{", "}").trim()
            if (body.isEmpty()) return

            val entries = splitTopLevelEntries(body)
            for (entry in entries) {
                val colonIndex = findColon(entry)
                if (colonIndex == -1) continue

                val rawKey = entry.substring(0, colonIndex).trim()
                val key = rawKey.removeSurrounding("\"")
                val rawValue = entry.substring(colonIndex + 1).trim()
                val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"

                when {
                    rawValue.startsWith("{") -> parseObject(rawValue, fullKey, result)
                    rawValue.startsWith("\"") -> result[fullKey] = unescapeString(rawValue)
                    rawValue == "null" -> result[fullKey] = ""
                    else -> result[fullKey] = rawValue // numbers, booleans
                }
            }
        }

        private fun findColon(entry: String): Int {
            var inString = false
            var escape = false
            for (i in entry.indices) {
                val c = entry[i]
                if (escape) { escape = false; continue }
                if (c == '\\') { escape = true; continue }
                if (c == '"') { inString = !inString; continue }
                if (c == ':' && !inString) return i
            }
            return -1
        }

        private fun splitTopLevelEntries(body: String): List<String> {
            val entries = mutableListOf<String>()
            var depth = 0
            var inString = false
            var escape = false
            var start = 0

            for (i in body.indices) {
                val c = body[i]
                if (escape) { escape = false; continue }
                if (c == '\\') { escape = true; continue }
                if (c == '"') { inString = !inString; continue }
                if (inString) continue
                when (c) {
                    '{', '[' -> depth++
                    '}', ']' -> depth--
                    ',' -> if (depth == 0) {
                        entries.add(body.substring(start, i).trim())
                        start = i + 1
                    }
                }
            }
            val last = body.substring(start).trim()
            if (last.isNotEmpty()) entries.add(last)
            return entries
        }

        private fun unescapeString(raw: String): String {
            val inner = raw.removeSurrounding("\"")
            return inner.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
        }
    }
}
