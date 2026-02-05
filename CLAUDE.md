# CLAUDE.md

## Project Overview

BattleTech Rules Engine is a multi-module Gradle project implementing BattleTech, turn-based, game rules.

The project uses:
- Gradle 9.3.1 with a modular architecture and convention plugins for build configuration
- Kotlin 2.3.0 
- JVM 25 with aligned Kotlin JVM target (JVM Toolchain)
- JUnit 6.0.2 with Jupiter API/Engine for testing

## Essential Commands

### Build and Test
```bash
# Build entire project
./gradlew build

# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :strategic:test
./gradlew :tactical:test
./gradlew :bt:test

# Run a single test class
./gradlew :strategic:test --tests "battletech.strategic.StrategicRulesTest"

# Clean and rebuild
./gradlew clean build

# Run the application
./gradlew :bt:run
```

## Architecture

### Module Structure

The project uses a layered module architecture:

- **`strategic/`**
  - Library module for strategic-level game rules (campaign movement, logistics, aerospace, etc.)
- **`tactical/`**
  - Library module for tactical-level game rules (combat, to-hit calculations, etc.)
- **`bt/`**
  - Application module that integrates strategic and tactical libraries
  - Application entry point is `battletech.MainKt`

Dependencies flow: `bt` → `strategic` + `tactical` (libraries are independent of each other)

### Convention Plugins (buildSrc/)

Build configuration is centralized using Gradle convention plugins to eliminate duplication:

- **`battletech.kotlin-common`** - Base configuration for all modules:
  - Kotlin JVM plugin
  - JUnit Platform setup with BOM
  - JVM toolchain configuration (version from `libs.versions.toml`)
  - Test logging configuration

- **`battletech.kotlin-library`** - For library modules:
  - Applies `kotlin-common`

- **`battletech.kotlin-application`** - For application modules:
  - Applies `kotlin-common`
  - Gradle `application` plugin

### Version Management

All versions are centralized in `gradle/libs.versions.toml`. The convention plugins dynamically read versions from the catalog using `VersionCatalogsExtension`. When adding dependencies or updating versions, always update `libs.versions.toml` first.
