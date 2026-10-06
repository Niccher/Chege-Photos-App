# Continuous Integration & Automation

Continuous integration pipelines, automated tests, and documentation verification for Chege Photos Android App.

---

## 1. CI Workflow Overview

The mobile repository utilizes GitHub Actions for continuous integration:

| Workflow | File Path | Trigger | Responsibilities |
|---|---|---|---|
| **Docs Quality Verification** | `.github/workflows/docs-verify.yml` | Push & PR to `main`/`master` touching `README.md` or `docs/**` | Runs `scripts/lint-docs.py` (checks line limits, leaked instructions, broken markdown links) and verifies external links via `markdown-link-check`. |
| **Android Build & Test** | `.github/workflows/android.yml` | Push & PR to `main`/`master` | Sets up JDK 17, caches Gradle dependencies, compiles debug APK, and runs unit tests. |

---

## 2. Running CI Checks Locally

### Documentation Linter
```bash
python3 scripts/lint-docs.py .
```

### Local Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Android Lint & Static Analysis
```bash
./gradlew lintDebug
```
