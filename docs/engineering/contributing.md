# Contributing Guide & Workflow

Engineering practices, Jetpack Compose conventions, Room schema migrations, and Definition of Done for Chege Photos Android App.

---

## 1. Branching & PR Workflow

1. Create a feature branch from `main`:
   ```bash
   git checkout -b feat/add-favorite-burst-toggle
   ```
2. Follow conventional commit messages:
   - `feat(ui): add multi-select action bar in PhotoGrid`
   - `fix(sync): retry WorkManager job on HTTP 503 exponential backoff`
   - `docs(android): update WorkManager constraints in android service doc`
3. Open a Pull Request targeting `main`.

---

## 2. Jetpack Compose Guidelines

* All UI screens reside in `app/src/main/java/.../ui/`.
* Follow unidirectional data flow (State flows down from ViewModels; Events flow up via lambdas).
* Mark stateless composables with `@Composable` and accept standard `modifier: Modifier = Modifier`.
* Provide `@Preview` annotations with light and dark theme wrappers.

---

## 3. Definition of Done (Mobile Changes)

Before merging a PR into `main`:

- [ ] **Compilation**: `./gradlew assembleDebug` compiles with 0 errors.
- [ ] **Tests**: Unit tests pass (`./gradlew testDebugUnitTest`).
- [ ] **Room Migrations**: Any modification to `@Entity` classes is accompanied by a versioned Room migration and schema JSON export (`schemas/`).
- [ ] **Clean Lint**: `./gradlew lintDebug` introduces 0 new lint errors.
- [ ] **Docs Verification**: `python3 scripts/lint-docs.py .` passes with 0 errors.
