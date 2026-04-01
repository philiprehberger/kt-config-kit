package com.philiprehberger.configkit

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertContains

class ConfigTest {

    @Test
    fun `map source basic string get`() {
        val cfg = config {
            map(mapOf("app.name" to "MyApp", "app.version" to "1.0"))
        }

        assertEquals("MyApp", cfg.getString("app.name"))
        assertEquals("1.0", cfg.getString("app.version"))
    }

    @Test
    fun `type conversion for int`() {
        val cfg = config {
            map(mapOf("port" to "8080"))
        }

        assertEquals(8080, cfg.getInt("port"))
    }

    @Test
    fun `type conversion for boolean`() {
        val cfg = config {
            map(mapOf(
                "enabled" to "true",
                "active" to "yes",
                "on" to "1",
                "off" to "false"
            ))
        }

        assertEquals(true, cfg.getBoolean("enabled"))
        assertEquals(true, cfg.getBoolean("active"))
        assertEquals(true, cfg.getBoolean("on"))
        assertEquals(false, cfg.getBoolean("off"))
    }

    @Test
    fun `type conversion for list`() {
        val cfg = config {
            map(mapOf("tags" to "a, b, c"))
        }

        val list = cfg.getList("tags")
        assertEquals(listOf("a", "b", "c"), list)
    }

    @Test
    fun `missing key returns null`() {
        val cfg = config {
            map(emptyMap())
        }

        assertNull(cfg.getString("nonexistent"))
        assertNull(cfg.getInt("nonexistent"))
        assertNull(cfg.getBoolean("nonexistent"))
        assertNull(cfg.getList("nonexistent"))
    }

    @Test
    fun `require throws on missing key`() {
        val cfg = config {
            map(emptyMap())
        }

        assertFailsWith<IllegalStateException> {
            cfg.require<String>("missing")
        }
    }

    @Test
    fun `require returns typed value`() {
        val cfg = config {
            map(mapOf("port" to "8080", "name" to "app"))
        }

        assertEquals(8080, cfg.require<Int>("port"))
        assertEquals("app", cfg.require<String>("name"))
    }

    @Test
    fun `multiple sources with override priority`() {
        val cfg = config {
            map(mapOf("db.host" to "localhost", "db.port" to "5432"))
            map(mapOf("db.host" to "production-db", "db.name" to "mydb"))
        }

        // Later source overrides earlier
        assertEquals("production-db", cfg.getString("db.host"))
        // Earlier source still provides non-overridden keys
        assertEquals("5432", cfg.getString("db.port"))
        // Later source adds new keys
        assertEquals("mydb", cfg.getString("db.name"))
    }

    @Test
    fun `env var key transformation converts UPPER_SNAKE to dot case`() {
        val cfg = config {
            map(mapOf("db.host" to "localhost"))
        }

        assertEquals("localhost", cfg.getString("db.host"))
    }

    @Test
    fun `default values returned for missing keys`() {
        val cfg = config {
            map(mapOf("existing" to "value"))
        }

        assertEquals("fallback", cfg.get("missing", "fallback"))
        assertEquals(3306, cfg.get("port", 3306))
        assertEquals(true, cfg.get("enabled", true))
    }

    @Test
    fun `default values not used when key exists`() {
        val cfg = config {
            map(mapOf("port" to "8080", "enabled" to "false"))
        }

        assertEquals(8080, cfg.get("port", 3306))
        assertEquals(false, cfg.get("enabled", true))
    }

    @Test
    fun `getList with custom delimiter`() {
        val cfg = config {
            map(mapOf("paths" to "/usr/bin;/usr/local/bin;/home/user/bin"))
        }

        val list = cfg.getList("paths", ";")
        assertEquals(3, list?.size)
        assertEquals("/usr/bin", list?.get(0))
    }

    @Test
    fun `int conversion returns null for non-numeric`() {
        val cfg = config {
            map(mapOf("value" to "not-a-number"))
        }

        assertNull(cfg.getInt("value"))
    }

    @Test
    fun `MapSource returns its map directly`() {
        val data = mapOf("a" to "1", "b" to "2")
        val source = MapSource(data)
        assertEquals(data, source.load())
    }

    @Test
    fun `EnvVarSource with prefix transforms keys`() {
        val source = EnvVarSource("UNLIKELY_PREFIX_XYZ123")
        val result = source.load()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `config builder with source function`() {
        val customSource = MapSource(mapOf("key" to "value"))
        val cfg = config {
            source(customSource)
        }

        assertEquals("value", cfg.getString("key"))
    }

    // --- JSON config source tests ---

    @Test
    fun `json source loads flat key-value pairs`() {
        val file = createTempJson("""{"host": "localhost", "port": "5432"}""")
        val cfg = config { jsonFile(file.absolutePath) }

        assertEquals("localhost", cfg.getString("host"))
        assertEquals("5432", cfg.getString("port"))

        file.delete()
    }

    @Test
    fun `json source flattens nested objects with dot notation`() {
        val file = createTempJson("""
        {
            "db": {
                "host": "localhost",
                "port": "5432"
            },
            "app": {
                "name": "myapp"
            }
        }
        """.trimIndent())
        val cfg = config { jsonFile(file.absolutePath) }

        assertEquals("localhost", cfg.getString("db.host"))
        assertEquals("5432", cfg.getString("db.port"))
        assertEquals("myapp", cfg.getString("app.name"))

        file.delete()
    }

    @Test
    fun `json source handles deeply nested objects`() {
        val file = createTempJson("""
        {
            "a": {
                "b": {
                    "c": "deep"
                }
            }
        }
        """.trimIndent())
        val cfg = config { jsonFile(file.absolutePath) }

        assertEquals("deep", cfg.getString("a.b.c"))

        file.delete()
    }

    @Test
    fun `json source handles numeric and boolean values as strings`() {
        val file = createTempJson("""{"count": 42, "active": true, "empty": null}""")
        val cfg = config { jsonFile(file.absolutePath) }

        assertEquals("42", cfg.getString("count"))
        assertEquals("true", cfg.getString("active"))
        assertEquals("", cfg.getString("empty"))

        file.delete()
    }

    @Test
    fun `json source participates in layered override`() {
        val file = createTempJson("""{"db.host": "json-host", "db.port": "3306"}""")
        val cfg = config {
            map(mapOf("db.host" to "default-host", "db.name" to "mydb"))
            jsonFile(file.absolutePath)
        }

        assertEquals("json-host", cfg.getString("db.host"))
        assertEquals("3306", cfg.getString("db.port"))
        assertEquals("mydb", cfg.getString("db.name"))

        file.delete()
    }

    // --- Variable interpolation tests ---

    @Test
    fun `simple variable interpolation`() {
        val cfg = config {
            map(mapOf(
                "base.url" to "https://example.com",
                "api.url" to "\${base.url}/api"
            ))
        }

        assertEquals("https://example.com/api", cfg.getString("api.url"))
    }

    @Test
    fun `nested variable interpolation`() {
        val cfg = config {
            map(mapOf(
                "host" to "localhost",
                "port" to "8080",
                "base" to "\${host}:\${port}",
                "url" to "http://\${base}/app"
            ))
        }

        assertEquals("http://localhost:8080/app", cfg.getString("url"))
    }

    @Test
    fun `circular reference detected`() {
        assertFailsWith<IllegalStateException> {
            config {
                map(mapOf(
                    "a" to "\${b}",
                    "b" to "\${a}"
                ))
            }.getString("a") // force lazy evaluation
        }
    }

    @Test
    fun `self-referencing key detected as circular`() {
        assertFailsWith<IllegalStateException> {
            config {
                map(mapOf("x" to "\${x}"))
            }.getString("x")
        }
    }

    @Test
    fun `interpolation with missing reference leaves placeholder`() {
        val cfg = config {
            map(mapOf("greeting" to "Hello \${name}"))
        }

        assertEquals("Hello \${name}", cfg.getString("greeting"))
    }

    // --- Enum parsing tests ---

    enum class LogLevel { DEBUG, INFO, WARN, ERROR }

    @Test
    fun `getEnum returns valid enum value`() {
        val cfg = config {
            map(mapOf("log.level" to "INFO"))
        }

        assertEquals(LogLevel.INFO, cfg.getEnum<LogLevel>("log.level"))
    }

    @Test
    fun `getEnum is case insensitive`() {
        val cfg = config {
            map(mapOf("log.level" to "debug"))
        }

        assertEquals(LogLevel.DEBUG, cfg.getEnum<LogLevel>("log.level"))
    }

    @Test
    fun `getEnum returns null for missing key`() {
        val cfg = config {
            map(emptyMap())
        }

        assertNull(cfg.getEnum<LogLevel>("log.level"))
    }

    @Test
    fun `getEnum throws for invalid value`() {
        val cfg = config {
            map(mapOf("log.level" to "INVALID"))
        }

        val ex = assertFailsWith<IllegalArgumentException> {
            cfg.getEnum<LogLevel>("log.level")
        }
        assertContains(ex.message ?: "", "INVALID")
    }

    // --- getOrDefault tests ---

    @Test
    fun `getStringOrDefault returns value when present`() {
        val cfg = config { map(mapOf("key" to "value")) }
        assertEquals("value", cfg.getStringOrDefault("key", "default"))
    }

    @Test
    fun `getStringOrDefault returns default when missing`() {
        val cfg = config { map(emptyMap()) }
        assertEquals("default", cfg.getStringOrDefault("missing", "default"))
    }

    @Test
    fun `getIntOrDefault returns value when present`() {
        val cfg = config { map(mapOf("port" to "9090")) }
        assertEquals(9090, cfg.getIntOrDefault("port", 3306))
    }

    @Test
    fun `getIntOrDefault returns default when missing`() {
        val cfg = config { map(emptyMap()) }
        assertEquals(3306, cfg.getIntOrDefault("port", 3306))
    }

    @Test
    fun `getIntOrDefault returns default for non-numeric value`() {
        val cfg = config { map(mapOf("port" to "abc")) }
        assertEquals(3306, cfg.getIntOrDefault("port", 3306))
    }

    @Test
    fun `getBooleanOrDefault returns value when present`() {
        val cfg = config { map(mapOf("debug" to "yes")) }
        assertEquals(true, cfg.getBooleanOrDefault("debug", false))
    }

    @Test
    fun `getBooleanOrDefault returns default when missing`() {
        val cfg = config { map(emptyMap()) }
        assertEquals(true, cfg.getBooleanOrDefault("debug", true))
    }

    @Test
    fun `getListOrDefault returns value when present`() {
        val cfg = config { map(mapOf("items" to "a,b,c")) }
        assertEquals(listOf("a", "b", "c"), cfg.getListOrDefault("items", default = emptyList()))
    }

    @Test
    fun `getListOrDefault returns default when missing`() {
        val cfg = config { map(emptyMap()) }
        val default = listOf("x", "y")
        assertEquals(default, cfg.getListOrDefault("items", default = default))
    }

    @Test
    fun `getListOrDefault with custom delimiter`() {
        val cfg = config { map(mapOf("paths" to "/a;/b;/c")) }
        assertEquals(listOf("/a", "/b", "/c"), cfg.getListOrDefault("paths", ";", emptyList()))
    }

    // --- toMap tests ---

    @Test
    fun `toMap returns all resolved config entries`() {
        val cfg = config {
            map(mapOf("a" to "1", "b" to "2", "c" to "3"))
        }

        val map = cfg.toMap()
        assertEquals(3, map.size)
        assertEquals("1", map["a"])
        assertEquals("2", map["b"])
        assertEquals("3", map["c"])
    }

    @Test
    fun `toMap returns interpolated values`() {
        val cfg = config {
            map(mapOf("host" to "localhost", "url" to "http://\${host}"))
        }

        val map = cfg.toMap()
        assertEquals("http://localhost", map["url"])
    }

    @Test
    fun `toMap returns empty map for empty config`() {
        val cfg = config { map(emptyMap()) }
        assertTrue(cfg.toMap().isEmpty())
    }

    // --- validate tests ---

    @Test
    fun `validate passes when all keys present`() {
        val cfg = config {
            map(mapOf("db.host" to "localhost", "db.port" to "5432", "db.name" to "mydb"))
        }

        // Should not throw
        cfg.validate("db.host", "db.port", "db.name")
    }

    @Test
    fun `validate throws when keys are missing`() {
        val cfg = config {
            map(mapOf("db.host" to "localhost"))
        }

        val ex = assertFailsWith<IllegalStateException> {
            cfg.validate("db.host", "db.port", "db.name")
        }
        assertContains(ex.message ?: "", "db.port")
        assertContains(ex.message ?: "", "db.name")
    }

    @Test
    fun `validate throws with single missing key`() {
        val cfg = config {
            map(mapOf("a" to "1"))
        }

        val ex = assertFailsWith<IllegalStateException> {
            cfg.validate("a", "b")
        }
        assertContains(ex.message ?: "", "b")
    }

    @Test
    fun `validate passes with no required keys`() {
        val cfg = config { map(emptyMap()) }
        cfg.validate() // Should not throw
    }

    @Test
    fun `has returns true for existing key`() {
        val cfg = config {
            map(mapOf("db.host" to "localhost"))
        }
        assertTrue(cfg.has("db.host"))
    }

    @Test
    fun `has returns false for missing key`() {
        val cfg = config {
            map(mapOf("db.host" to "localhost"))
        }
        assertFalse(cfg.has("db.port"))
    }

    @Test
    fun `getLong returns long value`() {
        val cfg = config {
            map(mapOf("max.size" to "9999999999"))
        }
        assertEquals(9999999999L, cfg.getLong("max.size"))
    }

    @Test
    fun `getLong returns null for missing key`() {
        val cfg = config {
            map(mapOf("a" to "1"))
        }
        assertNull(cfg.getLong("missing"))
    }

    @Test
    fun `getLong returns null for non-numeric value`() {
        val cfg = config {
            map(mapOf("key" to "not-a-number"))
        }
        assertNull(cfg.getLong("key"))
    }

    @Test
    fun `getLongOrDefault returns value when present`() {
        val cfg = config {
            map(mapOf("size" to "42"))
        }
        assertEquals(42L, cfg.getLongOrDefault("size", 0L))
    }

    @Test
    fun `getLongOrDefault returns default when missing`() {
        val cfg = config {
            map(mapOf("a" to "1"))
        }
        assertEquals(100L, cfg.getLongOrDefault("missing", 100L))
    }

    @Test
    fun `getDouble returns double value`() {
        val cfg = config {
            map(mapOf("rate" to "3.14"))
        }
        assertEquals(3.14, cfg.getDouble("rate"))
    }

    @Test
    fun `getDouble returns null for missing key`() {
        val cfg = config {
            map(mapOf("a" to "1"))
        }
        assertNull(cfg.getDouble("missing"))
    }

    @Test
    fun `getDouble returns null for non-numeric value`() {
        val cfg = config {
            map(mapOf("key" to "abc"))
        }
        assertNull(cfg.getDouble("key"))
    }

    @Test
    fun `getDoubleOrDefault returns value when present`() {
        val cfg = config {
            map(mapOf("rate" to "2.5"))
        }
        assertEquals(2.5, cfg.getDoubleOrDefault("rate", 0.0))
    }

    @Test
    fun `getDoubleOrDefault returns default when missing`() {
        val cfg = config {
            map(mapOf("a" to "1"))
        }
        assertEquals(9.99, cfg.getDoubleOrDefault("missing", 9.99))
    }

    @Test
    fun `has works after interpolation`() {
        val cfg = config {
            map(mapOf("host" to "localhost", "url" to "http://\${host}"))
        }
        assertTrue(cfg.has("url"))
        assertTrue(cfg.has("host"))
        assertFalse(cfg.has("missing"))
    }

    @Test
    fun `getPrefix returns matching entries with prefix stripped`() {
        val cfg = config {
            map(mapOf(
                "db.host" to "localhost",
                "db.port" to "5432",
                "db.name" to "mydb",
                "app.name" to "test"
            ))
        }
        val dbConfig = cfg.getPrefix("db")
        assertEquals(3, dbConfig.size)
        assertEquals("localhost", dbConfig["host"])
        assertEquals("5432", dbConfig["port"])
        assertEquals("mydb", dbConfig["name"])
    }

    @Test
    fun `getPrefix returns empty map when no keys match`() {
        val cfg = config {
            map(mapOf("app.name" to "test"))
        }
        val result = cfg.getPrefix("db")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getPrefix works with trailing dot`() {
        val cfg = config {
            map(mapOf("db.host" to "localhost", "db.port" to "5432"))
        }
        val result = cfg.getPrefix("db.")
        assertEquals(2, result.size)
        assertEquals("localhost", result["host"])
    }

    @Test
    fun `getPrefix does not match partial key names`() {
        val cfg = config {
            map(mapOf("database.host" to "localhost", "db.host" to "other"))
        }
        val result = cfg.getPrefix("db")
        assertEquals(1, result.size)
        assertEquals("other", result["host"])
    }

    // --- helpers ---

    private fun createTempJson(content: String): File {
        val file = File.createTempFile("config-test", ".json")
        file.writeText(content)
        return file
    }
}
