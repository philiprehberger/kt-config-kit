# Changelog

All notable changes to this library will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.1] - 2026-03-18

- Fix CI badge and gradlew permissions

## [0.1.0] - 2026-03-17

### Added
- `Config` class with type-safe accessors: `require()`, `get()`, `getString()`, `getInt()`, `getBoolean()`, `getList()`
- `config { }` DSL for building layered configuration from multiple sources
- `EnvVarSource` for reading environment variables with UPPER_SNAKE to dot.case key conversion
- `MapSource` for in-memory configuration
- `PropertiesFileSource` for reading `.properties` files
- Layered override semantics: later sources override earlier ones
