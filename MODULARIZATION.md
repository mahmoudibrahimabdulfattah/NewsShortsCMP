# Modularization progress

Splitting the monolithic `:composeApp` into `:core:*` and `:feature:*` modules.
The full spec is in [docs/modularization-plan.md](docs/modularization-plan.md).

**Read the plan's "Review corrections" table before proposing anything** — it
lists ideas that were checked against the code and rejected, so they do not get
re-proposed.

## Rules

- One phase = one branch = one PR, merged to `master` before the next starts.
- Inside a phase, commit at every point the build is green.
- Tick a phase here only when its verification commands pass. That tick is the
  last commit of the phase's PR.
- Never start phase *n+1* with phase *n* unverified.

## The verification command

```bash
./gradlew check :composeApp:assembleDebug -x lintDebug
```

Not `./gradlew build`, and not a bare `./gradlew check`. Both fail on `master`
for reasons that predate this work — see *The verification baseline* in the plan
for why, and for what to do instead of checking for a configuration-cache hit.

## Phases

Semantics first (1–3), Gradle splitting second (4–10). Phases 1–3 change Kotlin
only, so they carry design risk but no build risk. Phases 4–10 move files whose
packages are already acyclic, so they carry build risk but no design risk.

| # | Phase | Status |
|---|---|---|
| 0 | build-logic, convention plugins, catalog, cache-correct BuildConfig | **done** |
| 1 | Shared services; delete VM-in-VM injection (in place) | not started |
| 2 | Decompose `NewsViewModel` and `NewsUiState` (in place) | not started |
| 3 | Break the package cycles (package moves only) | not started |
| 4 | `:core:contract` + `:core:testing` + server de-duplication | not started |
| 5 | `:core:config`, `:core:model`, `:core:domain` | not started |
| 6 | `:core:data` | not started |
| 7 | `:core:localization` + `:core:ui` | not started |
| 8 | `:core:navigation` + `Navigator` | not started |
| 9 | The six `:feature:*` modules | not started |
| 10 | DI restructure + `viewModel { }` switch | not started |
| 11 | Optional cleanups | not started |

## Notes

Anything learned during a phase that the plan did not anticipate goes here, so
the next session does not rediscover it.

**Phase 0.** Two `check` failures predate this work and reproduce on `master`:
`verifyReleaseSigning` (no release keystore on a dev machine — the task is meant
to fail closed) and `lintDebug` (4 `RestrictedApi` errors on `androidx.glance`
in `TopStoryWidget.kt`). Hence the `-x lintDebug` above. The lint errors are
listed as a Phase 11 cleanup; while they stand, lint regressions cannot be
caught.

**Phase 0.** The configuration cache is never reused on this machine — the
Gradle daemon's own log file is tracked as a configuration input and changes
every run. Same on `master`, so it is environmental. Verify cache correctness by
changing an input and watching the output change, not by looking for a cache
hit.

**Phase 0.** Resolved `androidDebugRuntimeClasspath` was compared against
`master` before and after: 555 artifacts, zero difference. Worth repeating on
any phase that touches dependency declarations — it is the cheapest way to catch
a silent version drift.
