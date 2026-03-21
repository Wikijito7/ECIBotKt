---
name: quality-check
description: CRITICAL: Load BEFORE opening any PR. Missing this skill = failing detekt/test gates and rejected PRs. Validates all blockers: build, detekt, tests, coverage. Not for day-to-day coding — pre-PR validation only.
---

## When to use me
- At the end of a task before opening a PR
- During PR review to identify blockers and validate locally

## Not intended for
- Day-to-day coding guidance → use `architecture`, `testing`, `code-quality`
- Code review → use `code-review`

---

## Quality Gates (MUST)

| Gate | Command | Status |
|------|---------|--------|
| Build | `./gradlew build` | Must pass |
| Detekt | `./gradlew detekt` | Must pass |
| Tests | `./gradlew test` | Must pass |
| Coverage | `./gradlew jacocoTestReport` | Must not drop |

---

## Step 0 — Detect Changed Files (MANDATORY)

```bash
git diff --name-only HEAD
```

Read **every changed file** before running gates.

---

## Step 1 — Build

```bash
./gradlew build
```

Must compile without errors. Set timeout: 600000ms (10 min).

---

## Step 2 — Detekt (Auto-correct first)

Detekt has auto-correctable issues. Fix first:

```bash
# Auto-fix
./gradlew detekt --auto-correct

# Check remaining
./gradlew detekt
```

Set timeout: 300000ms (5 min).

---

## Step 3 — Tests

```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests "RadioCountryCodesCommandTest"

# Pattern
./gradlew test --tests "*Radio*"
```

Set timeout: 600000ms (10 min).

---

## Step 4 — Coverage (Optional but Recommended)

```bash
./gradlew jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

**Requirements:**
- Minimum: 80% line coverage for new code
- Target: 100% for critical paths

---

## What to Check (by change type)

| Change Type | Required Gates |
|-------------|----------------|
| Logic changes | Build + Tests + Coverage |
| UI/Embed changes | Build + Detekt |
| New commands | Build + Tests + Detekt (all 8 registration steps) |
| Dependency changes | Build + Full test suite |
| Localization | Build (keys must be generated) |

---

## CRITICAL: Run Sequentially

```
Build → Detekt → Tests → Coverage
```

Never run in parallel. Order matters — don't test code that doesn't compile.

---

## Reporting Format

- **BLOCKER**: Failing build, failing tests, detekt errors, coverage drops
- **WARNING**: Non-blocking improvements

---

## References
- `.github/copilot-instructions.md` — build commands
