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

From phase 4 on, a change to `:core:contract`, `build-logic` or `server/Dockerfile`
also needs the deploy path checked, which that command cannot see:

```bash
./server/deploy/verify-image-build.sh
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
| 2 | Decompose `NewsViewModel` and `NewsUiState` (in place) | **done** |
| 3 | Break the package cycles (package moves only) | **done** |
| 4 | `:core:contract` + server de-duplication | **done** |
| 5 | `:core:config`, `:core:model`, `:core:domain` | **done** |
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

**Phase 3 — the packages went further than the plan asked.** The plan wanted
only the moves that break the three cycles. What landed is the whole
`core/{contract,model,domain,data}` hierarchy, so `com.mk.newsshorts.domain.*`,
`data.*` and `sync.*` no longer exist. That is still only package moves, and it
makes phases 5 and 6 a `git mv` of whole trees rather than a scatter — but it
means those phases are now smaller than the plan's effort estimates suggest.

Left behind on purpose: `navigation/` (phase 8 owns it) and the Android
implementations under `analytics/`, `auth/`, `security/`, `notifications/`,
which follow their interfaces into `:core:data`'s androidMain in phase 6.

**Phase 3 — `checkPackageLayering` guards the tiers now.** It is a `SourceTask`
in build-logic wired into `check`. It has one exception, for the Android FCM
service reaching `NotificationBus`: that service is instantiated by the system
from the manifest, so it cannot leave `composeApp/androidMain`.

Its first version called `project.projectDir` inside `@TaskAction`.
`getProject()` throws on a configuration-cache **hit**, and this machine never
gets one, so it would have failed first on someone else's build. It now takes a
`DirectoryProperty` captured at registration. Worth remembering for every task
build-logic gains: the rule from phase 0 is that no task action may touch
`Project`.

**Phase 2 — the tab did not move.** `NewsUiState.currentTab` stayed with
`FeedViewModel` rather than going to `AppShellViewModel` with the overlay stack.
Switching tabs both moves the reader and decides which feed loads; splitting
those two halves before phase 8 gives both sides a `Navigator` to read would
only replace the coupling with an event bus, or with the screen dispatching one
tap twice. Phase 8 is where it moves.

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

**Phase 4 — `:core:testing` moved to phase 6.** The plan put it here, arguing
the shared fakes need a home before the tests that use them move out. They
cannot have one yet: `FakeAuthClient`, `FakeRemoteSyncClient` and
`FakeSavedArticlesLocalStore` implement interfaces that still live in
`:composeApp`, so a `:core:testing` module would depend on `:composeApp` while
`:composeApp`'s own tests depend on it — a Gradle project cycle. It lands in
phase 6, once `:core:domain` and `:core:data` are real modules.

**Phase 4 — the deploy break a green build cannot see.** `:server` now depends
on `:core:contract`, and the image has no Android SDK, so the contract's
convention plugin gates its targets on `-Pnewsshorts.contract.targets=jvm`. The
gating was correct on the first try and the deploy still failed:

```text
Could not find com.android.tools.build:gradle:8.11.2.
    Required by: project :core:contract > project :build-logic:convention
```

`build-logic/convention` declares `implementation(libs.android.gradlePlugin)`,
so AGP is a *runtime* dependency of the convention plugin jar and lands on the
plugin classpath of every project applying any plugin from it — including one
that never applies AGP. `build-logic`'s own settings have `google()` so the
plugin compiles; the deploy settings did not, so the consuming build could not
resolve it. `server/deploy/settings.gradle.kts` now carries the same filtered
`google()` the root settings use. The image resolves the AGP jar and still
never needs the SDK; those are two different things.

There is no docker on this machine, so `server/deploy/verify-image-build.sh`
copies exactly the paths the Dockerfile COPYs into a clean tree with the SDK
environment variables unset and runs the same command. It was green on `master`
before the phase started, which is what made the failure above trustworthy.
Keep its COPY list in step with the Dockerfile.

**Phase 4 — the two `ArticleDeepLinks` are not duplicates.** The server's builds
and parses links with `java.net.URLEncoder` and `java.net.URI`; the contract's
holds constants and the share-page URL helpers. Rewriting the server's
percent-encoding in common Kotlin to merge them would change the bytes of links
already sitting in people's chat windows. Only `SCHEME` and `HOST` were shared,
which the server now references rather than redeclaring.

**Phase 4 — how the slug was proven unchanged.** The plan asked for a
byte-identical `generateStaticFeed` diff, which is not achievable: that task
fetches live RSS and summarises it, so two runs never match. What actually
needed proving was the slug, so the corpus was generated from an independently
written implementation of FNV-1a/base36 rather than from the Kotlin under
change, and run through the merged `ShareSlug` on both `jvmTest` and
`iosSimulatorArm64Test`. Twelve URLs including Arabic and percent-encoded ones,
all matching — and matching the literals the two old suites pinned. Reach for
an independent implementation whenever the acceptance criterion is "this exact
value must not move".

**Phase 4 — `NewsCategory` was not rewritten to derive from `NewsCategories`.**
The enum's declaration order drives the reader's category tabs; the contract
set's order drives the server's feed iteration. Deriving either from the other
would silently reorder something a reader sees. A test asserts they match as
**sets**, so membership cannot drift while each keeps its own order.

**Phase 5 — a module boundary is not only a build change.** The packages did not
move and not one import needed editing, and the phase still broke the build
twice, both times on things that are legal inside a module and illegal across
one:

- **Smart casts stop at the boundary.** `AppShellViewModel` guarded
  `outcome.settings == null` and then used it; once `SyncOutcome` lived in
  `:core:domain`, Kotlin refused, because from outside the module nothing rules
  out a custom getter returning something different on the second read. Bind to
  a local `val` — that is the honest fix, not a workaround, since it makes the
  read-once assumption the guard already depended on explicit.
- **`internal` is module-scoped.** `AccountSyncUseCaseTest` stayed in
  `:composeApp` because it needs the shared fakes, and one of its cases
  constructed `ConflatedRemoteWriter` directly. That case moved into
  `:core:domain`'s own test source set rather than widening the class to
  `public` — a production class should not lose its encapsulation so a test in
  another module can see it.

Phase 6 moves far more code and will hit both again. When deciding whether a
test can stay behind, check what it *touches*, not only what it imports.

**Phase 5 — `api` vs `implementation` matters for coroutines.** `:core:domain`
exposes `StateFlow` and `SharedFlow` in its own public signatures
(`SettingsPersistence.preferences`, `FeedInvalidator.signals`), so coroutines
belong there as `api`. With `implementation` it still built — but only because
`:composeApp` happened to declare coroutines itself, which means the module was
relying on its consumer to supply part of its own API. `:core:model` keeps both
of its dependencies as `implementation`; neither coroutines nor `io.ktor.http.Url`
appears in a public signature there.

**Phase 5 — two things the plan asked for were deliberately not done.**
`GetTopHeadlinesUseCase` was not renamed to `FeedRepository`: the plan pairs
that rename with "while every call site is already being touched", and in this
phase no call site is touched at all. Doing it here would have turned a
zero-import-change phase into one editing every feed call site, and mixed a
design change into a move commit. It also needs a decision about
`core/domain/repository/NewsRepository.kt`, which already holds the obvious
name. Phase 9. Likewise `SyncPublisher`'s implementation and
`PushSubscriptionSynchronizer` stay in `:core:domain` until phase 6 moves them
with the rest of the data layer — they depend only on `core.model` and
coroutines, so they are not a layering problem where they are.
