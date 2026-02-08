# CLAUDE.md

## Project Overview

BattleTech Rules Engine is a multi-module Gradle project implementing BattleTech, turn-based, game rules.

## Technology Stack

- **Gradle**: 9.3.1
  - Kotlin DSL
  - modular architecture and convention plugins for build configuration in `buildSrc/`
- **Kotlin**: 2.3.0
- **JVM**: 25 (LTS) with aligned Kotlin JVM target (JVM Toolchain)
- **JUnit**: 6.0.2 with Jupiter API/Engine for testing

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
