---
name: gradle-build
description: Apply when modifying Gradle build files, adding dependencies, creating modules, or updating convention plugins
---

## Convention Plugins (buildSrc/)

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

## Version Management

All versions are centralized in `gradle/libs.versions.toml`.

When adding dependencies or updating versions, always update `libs.versions.toml` first.

The convention plugins dynamically read versions from the catalog using `VersionCatalogsExtension`.
