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
| 1 | Shared services; delete VM-in-VM injection (in place) | **done** |
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

**Phase 1 — `runTest` and long-lived coroutines.** This cost most of the phase,
so it is worth stating plainly for the phases that still have ViewModels to
decompose.

`runTest` will not finish while a coroutine started in the test's own scope is
still running, and a collector or a `SupervisorJob` never finishes.
`backgroundScope` is usable for work the test deliberately leaves running, but
it has to be driven precisely. Verified directly:

- `backgroundScope.launch { ... }` followed by `runCurrent()` runs the body.
- The same launch followed by `advanceUntilIdle()` does not run the body.
- A `delay` inside `backgroundScope` never advances, even after `runCurrent()`
  then `advanceUntilIdle()`.

So a long-lived collector can use `backgroundScope` when the test only needs
current-time work and calls `runCurrent()`. If the class under test needs a fake
delay to elapse, that coroutine cannot be in `backgroundScope`; pass the test
scope itself and ensure no endless coroutine is left running at the end.

For the phase 1 sync/settings cases, the way out was not to pick a scope but to
remove the endless coroutine:

- `SyncPublisher` has no auth collector. The observer that already reacts to
  sign-in calls `discardQueued()` instead.
- `SettingsViewModel` seeds its state from the store instead of collecting it.
- The writer's `SupervisorJob` is deliberately *not* a child of the caller's
  scope, because as a child it is itself a coroutine that never completes.

**Phase 2 — Kotlin/Native rejects commas in backtick test names.** A name like
``fun `ticked, and saved at the end`()`` compiles for Android and JVM and fails
`compileTestKotlinIosSimulatorArm64` with "Name contains illegal characters".
This is what `check → iosSimulatorArm64Test` is for; without it the break would
have reached `master` and only surfaced on someone's iOS build.

**Phase 1 — a failing test can point at the wrong thing.** Two account-switch
tests failed with `UncompletedCoroutinesError` naming a coroutine, while their
assertions passed. The real cause was that routing them through the real caller
also started a hydration, and hydration waits for a local list the test never
loaded. Print the values before believing the message.
