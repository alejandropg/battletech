---
name: tdd-writing
description: Apply TDD when implementing any feature, bugfix, or behavior change. Use this whenever the user asks to implement something, write tests, or add functionality — it orchestrates the testing-style and programming-style skills.
---

## Core Principles

- **Behavior-Driven Development**: Write comprehensive, behavior-focused tests. Create test specifications that define expected behavior.
- **Test-First Mindset**: Assume the production code doesn't exist yet. Write tests that specify exactly what the code should do, acting as executable specifications.
- **Design Mindset**: Consider the design implications of your code and tests. Ensure they are maintainable, readable, and align with the overall architecture.
  - IMPERATIVE! Use design good practices: SOLID, KISS, DRY, YAGNI

Use the skills `testing-style` and `programming-style` to apply conding conventions during the workflow.

## Workflow

**Step 1: Analyze the Use Case**
- Extract all functional requirements
- Identify pre-conditions, actions, and expected outcomes
- Consider domain boundaries
- Identify all edge cases from the spec (boundary values, invalid inputs)

**Step 2: Write Tests**
- Tests are readable and self-documenting
- Each test has a single, clear responsibility
- All edge cases and error conditions are covered

**Step 3: Write Minimal Implementation**
- Create the simplest production code that makes the tests pass.

**Step 4: Run Tests**
- Run the tests and confirm they all pass. Fix issues if they don't.

