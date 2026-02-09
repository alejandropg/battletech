---
name: programming-style
description: Apply patterns and conventions when writing or editing Kotlin code
---

## File Organization

- One top-level class per file (inner classes and nested classes are allowed)
- File name must match the top-level class name
- When 4-5+ related classes emerge, create a dedicated sub-package to group them rather than multiple files in the parent package

## Explicit Visibility

- Always write visibility modifiers explicitly for all declarations: classes, interfaces, objects, functions, properties, constructors
- Never rely on Kotlin's default visibility (public), and always explicitly specify the `public` modifier
- Use the most restrictive visibility that makes sense for the context

### Exceptions

- **Primary constructor**: The constructor itself doesn't need the `constructor` keyword with a visibility modifier when its visibility matches the class
  ```kotlin
  // Good - no "public constructor()" needed
  public class Foo(
      public val bar: String
  )
  
  // Required when visibility differs
  public class Foo private constructor(
      public val bar: String
  )
  ```
  - **Interface members**: Functions and properties are always public, no modifier needed
  - **Testing**
    - Test classes: always `internal`
    - Test functions: no visibility modifier

## Primary Constructor Properties

Each property declared in a primary constructor must be written on a separate line.

```kotlin
// Good - each property on its own line
public class Unit(
    public val id: UnitId,
    public val name: String
)

// Bad - properties on same line
public class Unit(public val id: UnitId, public val name: String)
```
