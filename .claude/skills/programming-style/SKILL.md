---
name: programming-style
description: Apply patterns and conventions when writing or editing Kotlin code
---

## Explicit Visibility

- Always write visibility modifiers explicitly for all declarations: classes, interfaces, objects, functions, properties, constructors
- Never rely on Kotlin's default visibility (public), and always explicitly specify the `public` modifier
- Use the most restrictive visibility that makes sense for the context

### Exceptions

- Primary constructor: no visibility modifier if it is the same as the class
- Interface functions and properties (always public, no modifier needed)

## File Organization

- One top-level class per file (inner classes and nested classes are allowed)
- File name must match the top-level class name
- When 4-5+ related classes emerge, create a dedicated sub-package to group them rather than multiple files in the parent package
