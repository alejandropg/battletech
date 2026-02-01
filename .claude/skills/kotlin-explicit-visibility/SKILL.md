---
name: kotlin-explicit-visibility
description: Enforce explicit visibility modifiers when writing or editing Kotlin source files
---

## Requirements

When writing or editing Kotlin code, you MUST follow these visibility rules:

**Explicit Visibility:**
- Always write visibility modifiers explicitly for all top-level declarations
- Never rely on Kotlin's default visibility (public)
- Apply to: classes, interfaces, objects, functions, properties, constructors
- Use always the most restrictive visibility that makes sense for the context

**Valid Visibility Modifiers:**
- `public` - visible everywhere (Kotlin default, but must be explicit)
- `private` - visible only in the same file/class
- `internal` - visible within the same module
- `protected` - visible in subclasses (class members only)

## Examples

**Classes and Interfaces:**
```kotlin
public class TodoList { }
private class InternalHelper { }
internal interface ModuleService { }
```

**Functions:**
```kotlin
public fun createTodo(): Todo { }
private fun validateInput(): Boolean { }
internal fun processInternal(): Unit { }
```

**Properties:**
```kotlin
public val items: List<Todo>
private val cache: MutableMap<String, Todo>
internal var moduleState: State
```

**Constructors:**
```kotlin
public class Todo private constructor(val id: String) { }
public class Service internal constructor(val repo: Repository) { }
```

## Verification

After writing Kotlin code, verify:
1. No class/function/property lacks a visibility modifier
2. All top-level and member declarations have explicit `public`, `private`, `internal`, or `protected`
3. The chosen visibility is the most restrictive that still allows required access

## Exceptions

- Local variables inside functions (no visibility modifiers apply)
- Interface functions and properties (always public, no modifier needed)
- Override members may inherit visibility, but prefer explicit when changing
