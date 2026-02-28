# CLAUDE.md

## Project Overview

BattleTech Rules Engine is a multi-module project implementing BattleTech, hexagonal board tabletop, turn-based, game rules.

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

# Build TUI fat JAR (single-file distributable, ~7 MB)
./gradlew :tui:shadowJar     # → tui/build/libs/tui.jar
./gradlew :tui:createExecutable  # → tui/build/tui (self-executing, Unix/macOS)

# Run the TUI application
# (Gradle always forks a JVM detached from the terminal, so use the JAR directly)
./gradlew :tui:shadowJar && java -jar tui/build/libs/tui.jar
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
- **`tui/`**
  - Terminal UI application using [Mordant](https://github.com/ajalt/mordant)
  - Uses the Shadow plugin (`com.gradleup.shadow`) to produce a fat JAR and self-executing binary
  - Entry point is `battletech.tui.MainKt`

Dependencies flow: `bt` → `strategic` + `tactical`, `tui` → `tactical` (libraries are independent of each other)

## Tool Preferences

- **Always use the LSP tool** for code intelligence operations: finding references, go-to-definition, hover info, document/workspace symbols, call hierarchy, implementations. Never fall back to Grep/Glob for tasks the LSP can handle.
