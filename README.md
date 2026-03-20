# config-kit

[![CI](https://github.com/philiprehberger/kt-config-kit/actions/workflows/publish.yml/badge.svg)](https://github.com/philiprehberger/kt-config-kit/actions/workflows/publish.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.philiprehberger/config-kit)](https://central.sonatype.com/artifact/com.philiprehberger/config-kit)
[![License](https://img.shields.io/github/license/philiprehberger/kt-config-kit)](LICENSE)

Layered configuration loading from multiple sources with type-safe access.

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.philiprehberger:config-kit:0.2.2")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.philiprehberger:config-kit:0.2.0'
}
```

### Maven

```xml
<dependency>
    <groupId>com.philiprehberger</groupId>
    <artifactId>config-kit</artifactId>
    <version>0.2.2</version>
</dependency>
```

## Usage

### Basic Configuration

```kotlin
import com.philiprehberger.configkit.*

val cfg = config {
    map(mapOf(
        "db.host" to "localhost",
        "db.port" to "5432",
        "debug" to "true"
    ))
}

val host: String = cfg.require("db.host")
val port: Int = cfg.get("db.port", 5432)
val debug: Boolean = cfg.get("debug", false)
```

### Layered Sources

Later sources override values from earlier sources:

```kotlin
val cfg = config {
    // Base defaults
    map(mapOf("db.host" to "localhost", "db.port" to "5432"))
    // JSON file overrides
    jsonFile("config.json")
    // Environment variables override everything
    envVars("APP")  // APP_DB_HOST -> db.host
}
```

### JSON Configuration

Load configuration from JSON files. Nested objects are flattened using dot notation:

```json
{
    "db": {
        "host": "localhost",
        "port": "5432"
    },
    "app": {
        "name": "myapp"
    }
}
```

```kotlin
val cfg = config {
    jsonFile("config.json")
}
cfg.getString("db.host") // "localhost"
cfg.getString("app.name") // "myapp"
```

### Environment Variables

`EnvVarSource` transforms UPPER_SNAKE_CASE keys to dot.case notation and optionally strips a prefix:

```kotlin
// APP_DB_HOST=production-db  ->  db.host=production-db
// APP_DB_PORT=5432           ->  db.port=5432
val cfg = config {
    envVars("APP")
}
cfg.getString("db.host") // "production-db"
```

### Variable Interpolation

Values containing `${key}` placeholders are automatically resolved:

```kotlin
val cfg = config {
    map(mapOf(
        "host" to "localhost",
        "port" to "8080",
        "base.url" to "http://${host}:${port}",
        "api.url" to "${base.url}/api"
    ))
}
cfg.getString("api.url") // "http://localhost:8080/api"
```

Circular references are detected and throw an `IllegalStateException`.

### Type-Safe Access

```kotlin
val name: String = cfg.require("app.name")       // throws if missing
val port: Int = cfg.get("app.port", 8080)         // returns default if missing
val tags: List<String>? = cfg.getList("app.tags") // splits by comma
```

### Enum Parsing

```kotlin
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

val level: LogLevel? = cfg.getEnum<LogLevel>("log.level") // case-insensitive
```

### Convenience Defaults

Typed `*OrDefault` methods avoid dealing with nulls:

```kotlin
val host = cfg.getStringOrDefault("db.host", "localhost")
val port = cfg.getIntOrDefault("db.port", 5432)
val debug = cfg.getBooleanOrDefault("debug", false)
val tags = cfg.getListOrDefault("tags", default = listOf("default"))
```

### Validation

Verify that all required keys are present:

```kotlin
cfg.validate("db.host", "db.port", "db.name")
// Throws IllegalStateException listing any missing keys
```

### Export

Export all resolved configuration as a flat map:

```kotlin
val allConfig: Map<String, String> = cfg.toMap()
```

## API

| Class / Function | Description |
|------------------|-------------|
| `config { }` | DSL builder for creating a `Config` instance |
| `Config.require(key)` | Gets a typed value, throwing if missing |
| `Config.get(key, default)` | Gets a typed value with a fallback default |
| `Config.getString(key)` | Gets the raw string value or null |
| `Config.getStringOrDefault(key, default)` | Gets the string value with a fallback |
| `Config.getInt(key)` | Gets the value as an Int or null |
| `Config.getIntOrDefault(key, default)` | Gets the Int value with a fallback |
| `Config.getBoolean(key)` | Gets the value as a Boolean or null |
| `Config.getBooleanOrDefault(key, default)` | Gets the Boolean value with a fallback |
| `Config.getList(key, delimiter)` | Splits the value into a list of strings |
| `Config.getListOrDefault(key, delimiter, default)` | Splits the value with a fallback list |
| `Config.getEnum<T>(key)` | Parses a value as an enum constant (case-insensitive) |
| `Config.toMap()` | Exports all resolved config as a flat map |
| `Config.validate(vararg keys)` | Throws if any required keys are missing |
| `EnvVarSource` | Reads from environment variables with UPPER_SNAKE to dot.case conversion |
| `MapSource` | In-memory configuration from a map |
| `PropertiesFileSource` | Reads from a `.properties` file |
| `JsonConfigSource` | Reads from a JSON file with nested object flattening |

## Development

```bash
./gradlew test       # Run tests
./gradlew check      # Run all checks
./gradlew build      # Build JAR
```

## License

MIT
