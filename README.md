# kt-config-kit

[![CI](https://github.com/philiprehberger/kt-config-kit/actions/workflows/ci.yml/badge.svg)](https://github.com/philiprehberger/kt-config-kit/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.philiprehberger/config-kit)](https://central.sonatype.com/artifact/com.philiprehberger/config-kit)

Layered configuration loading from multiple sources with type-safe access.

## Requirements

- Kotlin 1.9+ / Java 17+

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.philiprehberger:config-kit:0.1.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.philiprehberger:config-kit:0.1.0'
}
```

### Maven

```xml
<dependency>
    <groupId>com.philiprehberger</groupId>
    <artifactId>config-kit</artifactId>
    <version>0.1.0</version>
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
    // Environment variables override defaults
    envVars("APP")  // APP_DB_HOST -> db.host
    // Properties file overrides everything
    propertiesFile("config.properties")
}
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

### Type-Safe Access

```kotlin
val name: String = cfg.require("app.name")     // throws if missing
val port: Int = cfg.get("app.port", 8080)       // returns default if missing
val tags: List<String>? = cfg.getList("app.tags") // splits by comma
```

## API

| Class / Function | Description |
|------------------|-------------|
| `config { }` | DSL builder for creating a `Config` instance |
| `Config.require(key)` | Gets a typed value, throwing if missing |
| `Config.get(key, default)` | Gets a typed value with a fallback default |
| `Config.getString(key)` | Gets the raw string value or null |
| `Config.getInt(key)` | Gets the value as an Int or null |
| `Config.getBoolean(key)` | Gets the value as a Boolean or null |
| `Config.getList(key, delimiter)` | Splits the value into a list of strings |
| `EnvVarSource` | Reads from environment variables with UPPER_SNAKE to dot.case conversion |
| `MapSource` | In-memory configuration from a map |
| `PropertiesFileSource` | Reads from a `.properties` file |

## Development

```bash
./gradlew test       # Run tests
./gradlew check      # Run all checks
./gradlew build      # Build JAR
```

## License

MIT
