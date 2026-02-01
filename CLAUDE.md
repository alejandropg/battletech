# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BattleTech Rules Engine is a multi-module Gradle project implementing BattleTech game rules. The project uses Kotlin with JVM, plus JUnit for testing, and follows a modular architecture with convention plugins for build configuration.

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

- **`strategic/`** - Library module for strategic-level game rules (campaign movement, logistics, etc.)
- **`tactical/`** - Library module for tactical-level game rules (combat, to-hit calculations, etc.)
- **`bt/`** - Application module that integrates strategic and tactical libraries

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

### Package Structure
- Strategic rules: `battletech.strategic.*`
- Tactical rules: `battletech.tactical.*`
- Main application: `battletech.*` (in bt module)

## Key Points

- **JVM Toolchain**: Project uses JVM 25 with aligned Kotlin JVM target (configured dynamically from version catalog)
- **Testing**: JUnit 6.0.2 with Jupiter API/Engine. All tests use JUnit Platform.
- **Main Class**: Application entry point is `battletech.MainKt` in the bt module
