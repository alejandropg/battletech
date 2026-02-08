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
