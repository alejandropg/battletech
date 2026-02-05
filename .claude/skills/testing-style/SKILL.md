---
name: testing-style
description: Apply patterns and conventions when writing or editing JUnit tests with Kotlin
---

## Naming

- **Test classes**: Use descriptive names and never use `@DisplayName`
- **Test names**: Describe behavior, not implementation (e.g., "throw exception when id is empty")

## Structure

- Arrange-Act-Assert with blank lines separating sections (no comments needed)
- Extract repeated fixtures as class properties initialized in declaration
  - Use `@BeforeEach` only when setup logic is required
- Group related tests using nested classes if beneficial for clarity and readability

## Assertions

- Prefer JUnit over third-party libraries
- Use AssertJ only when it provides capabilities JUnit lacks (e.g., complex collection assertions)
- Do not assert nullability of non-nullable Kotlin types

## Mocking

- Use real classes within the same domain
- Mock only external dependencies or other domains/layers (databases, APIs, file systems, other modules)
- Use Mockito
- Prefer state-based testing over interaction verification
