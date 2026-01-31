# BattleTech Rules Engine

A multi-module Gradle project for BattleTech game rules implementation.

## Project Structure

```
battletech/
├── buildSrc/                       # Common build configuration
│   └── src/main/kotlin/
│       ├── battletech.kotlin-common.gradle.kts       # Shared Kotlin config
│       ├── battletech.kotlin-library.gradle.kts      # Library convention plugin
│       └── battletech.kotlin-application.gradle.kts  # Application convention plugin
├── gradle/
│   └── libs.versions.toml          # Version catalog (all versions defined here)
├── strategic/                      # Strategic rules library
│   └── src/
│       ├── main/kotlin/battletech/strategic/
│       └── test/kotlin/battletech/strategic/
├── tactical/                       # Tactical rules library
│   └── src/
│       ├── main/kotlin/battletech/tactical/
│       └── test/kotlin/battletech/tactical/
└── bt/                            # Main application (depends on strategic & tactical)
    └── src/
        ├── main/kotlin/battletech/
        └── test/kotlin/battletech/
```

## Technology Stack

- **Kotlin**: 2.3.0
- **JVM**: 25 (LTS)
- **JUnit**: 6.0.2
- **Gradle**: 9.3.1 with Kotlin DSL

## Building and Running

### Build the entire project
```bash
./gradlew build
```

### Run tests
```bash
./gradlew test
```

### Run the application
```bash
./gradlew :bt:run
```

### Clean build
```bash
./gradlew clean build
```

## Module Dependencies

- `bt` depends on both `strategic` and `tactical`
- `strategic` and `tactical` are independent libraries

## Version Management

All versions are centralized in `gradle/libs.versions.toml`:
- Kotlin version
- JVM toolchain version
- JUnit version
- All library dependencies

## Convention Plugins

The project uses Gradle convention plugins in `buildSrc/` to avoid duplication:

- **battletech.kotlin-common**: Base configuration for all Kotlin modules
- **battletech.kotlin-library**: For library modules (strategic, tactical)
- **battletech.kotlin-application**: For application modules (bt)
