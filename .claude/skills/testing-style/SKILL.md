---
name: testing-style
description: Apply patterns and conventions when writing or editing JUnit tests with Kotlin
---

## Naming

- **Test classes**: Use descriptive names and never use `@DisplayName`
- **Test names**: Describe behavior, not implementation (e.g., `fun \`throw exception when id is empty\`()`")

## Structure

- Arrange-Act-Assert with blank lines separating sections (no `// Arrange` comments)
- Extract shared fixtures as class-level properties initialized in declaration
  - Use `@BeforeEach` only when setup logic cannot be expressed as a property initializer
- Group related tests using nested classes blocks when it aids clarity

## Assertions

- Do not assert nullability of non-nullable Kotlin types
- Prefer JUnit to third-party libraries for simple value equality
- Use JUnit Kotlin extension functions where available (e.g., `import org.junit.jupiter.api.assertThrows`)
- Use AssertJ when it is more expressive — especially for:
  - Collections elements: `assertThat(list).contains(...)`, `assertThat(list).allSatisfy {...}`
  - Type checking: `assertThat(result).isInstanceOf(X::class.java)`
  - Strings: `assertThat(str).contains("foo")`


## Mocking

- Use real classes within the same domain
- Mock only external dependencies or other modules/layers (databases, APIs, file systems...)
- Use Mockk
- Prefer state-based testing to interaction verification
