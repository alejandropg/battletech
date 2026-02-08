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

- Do not assert nullability of non-nullable Kotlin types
- Prefer JUnit over third-party libraries
- Use AssertJ if more expressive than JUnit when asserts are about the elements of collections/iterables, strings/charsecuences (e.g., prefer AssertJ `assertThat(list).contains(foo, bar)` over JUnit `assertTrue(list.contains(foo, bar))`)

## Mocking

- Use real classes within the same domain
- Mock only external dependencies or other domains/layers (databases, APIs, file systems, other modules)
- Use Mockk
- Prefer state-based testing over interaction verification

## Visibility

- Test classes: always `internal`
- Test functions: no visibility modifier
