package com.philiprehberger.configkit

/**
 * DSL builder for creating a [Config] instance with layered sources.
 *
 * Sources added later take priority over earlier ones when keys overlap.
 *
 * ```
 * val cfg = config {
 *     map(mapOf("app.name" to "MyApp"))
 *     envVars("APP")
 * }
 * ```
 */
class ConfigBuilder {
    private val sources = mutableListOf<ConfigSource>()

    /**
     * Adds a generic [ConfigSource].
     *
     * @param s the source to add
     */
    fun source(s: ConfigSource) {
        sources.add(s)
    }

    /**
     * Adds an [EnvVarSource] with an optional prefix.
     *
     * @param prefix optional prefix for filtering and stripping environment variable names
     */
    fun envVars(prefix: String? = null) {
        sources.add(EnvVarSource(prefix))
    }

    /**
     * Adds a [PropertiesFileSource] that reads from the given path.
     *
     * @param path the file path to the `.properties` file
     */
    fun propertiesFile(path: String) {
        sources.add(PropertiesFileSource(path))
    }

    /**
     * Adds a [MapSource] with the given key-value pairs.
     *
     * @param m the configuration map
     */
    fun map(m: Map<String, String>) {
        sources.add(MapSource(m))
    }

    internal fun build(): Config {
        return Config(sources.toList())
    }
}

/**
 * Creates a [Config] instance using DSL syntax.
 *
 * Sources are applied in order: later sources override earlier ones.
 *
 * ```
 * val cfg = config {
 *     map(mapOf("db.host" to "localhost", "db.port" to "5432"))
 *     envVars("APP")
 * }
 *
 * val host: String = cfg.require("db.host")
 * val port: Int = cfg.get("db.port", 5432)
 * ```
 *
 * @param block the builder configuration
 * @return a configured [Config] instance
 */
fun config(block: ConfigBuilder.() -> Unit): Config {
    return ConfigBuilder().apply(block).build()
}
