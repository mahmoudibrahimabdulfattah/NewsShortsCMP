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

## Phases

Semantics first (1–3), Gradle splitting second (4–10). Phases 1–3 change Kotlin
only, so they carry design risk but no build risk. Phases 4–10 move files whose
packages are already acyclic, so they carry build risk but no design risk.

| # | Phase | Status |
|---|---|---|
| 0 | build-logic, convention plugins, catalog, cache-correct BuildConfig | in progress |
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
