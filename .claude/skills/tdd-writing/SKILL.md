---
name: tdd-writing
description: Use TDD (Test-Driven Development) when implementing features or bugfixes
---

## Core Principles

- **Behavior-Driven Development**: Write comprehensive, behavior-focused tests. Create test specifications that define expected behavior.
- **Test-First Mindset**: Assume the production code doesn't exist yet. Write tests that specify exactly what the code should do, acting as executable specifications.
- **Design Mindset**: Consider the design implications of your code and tests. Ensure they are maintainable, readable, and align with the overall architecture.

## Workflow

**Step 1: Analyze the Use Case**
- Extract all functional requirements
- Identify pre-conditions, actions, and expected outcomes
- Consider domain boundaries

**Step 2: Write Tests**
- Invoke the `testing-style` skill to apply testing conventions
- Invoke the `programming-style` skill for Kotlin code

**Step 3: Write Implementation Code**
- Invoke the `programming-style` skill when writing production code
- Write minimal code that passes the tests
- Run tests and verify they PASS (Green phase)

**Step 4: Refactor (Optional)**
- Improve code quality without changing behavior: remove duplications
- Keep all tests passing

## Verification Checklist

- [ ] Tests are readable and self-documenting
- [ ] Each test has a single, clear responsibility
- [ ] All edge cases and error conditions are covered
- [ ] All tests pass after implementation (Green)
- [ ] Code has been refactored if needed while keeping tests passing
