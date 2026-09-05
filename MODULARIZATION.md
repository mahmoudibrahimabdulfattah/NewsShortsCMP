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

At thirteen modules a full `check` is slow enough that a failure late in it
costs half an hour. Verify in stages instead, most important first — Android,
then iOS, then the rest — so a break shows up in minutes:

```bash
./gradlew :composeApp:assembleDebug testDebugUnitTest lintDebug
```

```bash
./gradlew iosSimulatorArm64Test
```

```bash
./gradlew jvmTest checkPackageLayering :server:test
```

```bash
./gradlew jsTest wasmJsTest
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
| 6a | `:core:data` + `:core:testing` (moves only) | **done** |
| 6b | `SettingsStorage` to an interface; collapse the no-op platform modules | **done** |
| 7 | `:core:localization` + `:core:ui` | **done** |
| 8 | `:core:navigation` + `Navigator` | **done** |
| 9 | The six `:feature:*` modules | **done** |
| 10 | DI restructure + `viewModel { }` switch | **done** |
| 11 | Optional cleanups | **done** |

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

**Phase 6 was split in two.** The plan bundles two refactors into the move:
`SettingsStorage` from `expect class` to interface, and collapsing the four
byte-identical no-op platform modules. The first one rewrites the paths user
settings persist through, and a drifted key silently loses a reader's choices.
Reviewing that inside a 60-file move is how such a change gets waved through, so
6a is the move alone and 6b is the two refactors.

**Phase 6 — the plan's stated reason for `:core:testing` is wrong.** It claims
the three shared fakes "exist only because `SettingsStorage` is an `expect
class`". They do not: `FakeAuthClient`, `FakeRemoteSyncClient` and
`FakeSavedArticlesLocalStore` implement `AuthClient`, `RemoteSyncClient` and
`SavedArticlesLocalStore`, all ordinary interfaces, and there is no
`FakeSettingsStorage` or `InMemorySettingsStorage` anywhere in the repo. So
`:core:testing` never depended on that refactor, which is what made the split
above possible.

**Phase 6 — `internal` decided where three tests live.** Grepping the moving
trees for `internal` before moving anything was worth more than any other check
in this phase. Most internals travel with their tests and are fine. Two had to
become public because a *user* outside the new module needs them —
`articleKey`, which `InboxViewModel` must key articles by exactly as the store
does, and `cappedForStorage`, which the fake uses so that a test cannot pass on
a list the real store would truncate. Both are contract rather than detail, so
widening them was right; the rest stayed `internal`.

`AccountSyncUseCaseTest` went to `:core:data`, not `:core:domain`: it wires the
use case, `DefaultSyncPublisher` and `DefaultSavedArticlesRepository` together,
so it is a data-layer integration test that only `:core:data` can see all of.

**Phase 6 — `isDebugBuild()` on Android, and the bug still open on four
targets.** The Android actual read `com.mk.newsshorts.BuildConfig.DEBUG`, the
*app* module's AGP flag, which a library cannot see. `:core:data` now enables
`buildConfig` for itself and reads its own. This is not cosmetic:
`AppGateViewModel` skips the device-integrity check when `isDebugBuild()` is
true, so a wrong value means a shipped build stops enforcing the root and
emulator policy.

Still open, deliberately untouched here: the `ios`, `jvm`, `js` and `wasmJs`
actuals all hardcode `return true`. Release desktop and web builds therefore run
with Ktor body logging on *and* skip the integrity check. It is a behaviour
change, it predates this work, and it needs its own commit.

**Phase 6 — the layering check now covers every module.** It was registered only
for `:composeApp`, so the tier rules stopped being enforced the moment code left
that module. `newsshorts.kmp.library` registers it unconditionally now.
`tierFor` returns null for unrecognised packages, so `com.mk.newsshorts.testing`
is skipped rather than failing. Resist writing `project.path == ":core:x"` into a
convention plugin — a shared plugin that knows one project's name becomes a
switch over the project list by phase 9.

**Phase 6b — a package rename had already silently wiped desktop settings,
twice.** `SettingsStorage.jvm.kt` used
`Preferences.userNodeForPackage(SettingsStorage::class.java)`, and
`userNodeForPackage` derives the node path from the class's **package**. That
package moved in `46a0e0c` ("Move the app onto its real package name") and again
in `fe49ef5` (phase 3, `data.local` → `core.data.local`). Each move rehomed every
desktop reader's settings to a fresh empty node. Nothing failed, nothing logged,
the app just started on defaults.

The node is now the literal `com/mk/newsshorts` under `userRoot()`, and the
constructor copies keys across from the two historical node paths when the
pinned node is empty — newest first, never overwriting. The general rule worth
carrying: **a storage location is not a code location.** Anything that derives a
persisted path, key, table or file name from a package, class or file name is a
refactor away from discarding user data, and the build stays green while it
happens.

Android, iOS and JS were unaffected — `SharedPreferences` is named by an
explicit string, and `NSUserDefaults.standardUserDefaults` and `localStorage`
are keyed by the key alone.

**Phase 6b — the key contract test.** `SettingsStorageKeyContractTest` in
`:core:data` asserts the persisted key strings as *literals* and drives the real
writers to produce them. A test that compares a constant against itself passes
no matter what the constant says, which is exactly the failure it exists to
catch. It covers the settings keys, the notification tiers, the store keys
(`saved_articles`, `recent_searches`, `seen_articles`, `notification_inbox_read`,
`notification_inbox_dismissed`, `pending_sign_in_email`,
`preferred_backend_origin`) and the news-cache prefixes. All of them were
cross-checked against a real device's `news_shorts_prefs.xml`.

**Phase 6b — the expect/actual pair moved rather than disappeared.** The plan
wanted `SettingsStorage`'s `expect class` gone. It is gone, but a small
`expect fun platformSettingsStorage(koin: Koin): SettingsStorage` took its place,
because the storage genuinely differs per platform — the plan says so itself —
and a `commonMain` binding cannot name `NSUserDefaults` or `java.util.prefs`.
The win is real but smaller than the plan implies: an `expect fun` instead of an
`expect class` (no `-Xexpect-actual-classes` warning), five one-line actuals
instead of four 26-line no-op DI modules, and the no-op clients now shared
classes in `:core:data`.

**Phase 6b — wasm persistence is named, not fixed.** wasmJs binds
`InMemorySettingsStorage`, which is what its `actual` always was. Settings still
do not survive a reload on wasm; the difference is that the class now says so.

**Still open after phase 6:** `isDebugBuild()` hardcodes `true` on ios, jvm, js
and wasmJs, so release desktop and web run with Ktor body logging on and skip the
device-integrity check.

**Phase 7 — `Navigation.kt` was two files wearing one name.** `presentation/mvi/`
held `ThemeMode`, `ArticleDetails`, `ArticleOpenOrigin`, `LanguageOption`,
`CountryOption`, `OnboardingStep` and `TextScale` next to `Overlay` and
`NavigationTab`. The first group is exactly what the plan's phase 5 contents
list named for `:core:model` — they were missed because phase 5 moved the
`core/model` package tree and these were sitting under `presentation`. They moved
here because `:core:ui`'s theme cannot compile without `ThemeMode`. `Overlay` and
`NavigationTab` stayed for phase 8.

`ThemeMode` and `TextScale` are persisted (`mode.name.lowercase()` and
`scale.stored`), so `ThemeModeTest` and `TextScaleTest` now pin `"system"`,
`"light"`, `"dark"` and every `stored` literal. Moving an enum does not change
its `name` — renaming a constant does, and that would reset readers' themes
silently. Same class of risk as phase 6b's prefs node.

**Phase 7 — a green build proves the least in this phase, and that held.** The
build passed while three things still needed eyes:

- The JS and Wasm IR link tasks ran out of heap at `kotlin.daemon.jvmargs=-Xmx3072M`
  once the Compose UI became its own module. Raised to 5 GB with a comment saying
  which tasks need it, so it does not get trimmed back as an arbitrary number.
- Resource packaging had to be checked per target rather than assumed: 11 fonts
  in the JS distribution, 11 in the Wasm distribution, 11 in the APK, 11 in the
  iOS framework, plus `logo.png`.
- The fonts had to be *looked at*. Tajawal renders for Arabic and Poppins for
  Latin on the same screen, and the splash logo comes through
  `appLogoPainter()`, the one accessor `:core:ui` exposes so `Res` can stay
  internal.

**Phase 7 — an incremental build reported resources twice; a clean one did not.**
Mid-review the APK looked like it shipped every font twice, ~1.2 MB of
duplication, under both `com.mk.newsshorts.core.ui.resources` and
`newsshorts.composeapp.generated.resources`. The second set was stale output
from before the move — the source files were already deleted in git.
`:composeApp:clean` then `assembleDebug` ships exactly 11, one copy, from
`:core:ui`. Worth knowing for any later phase that moves resources: **measure
packaging on a clean build**, because the resource copy tasks do not remove what
an earlier layout left behind.

**Phase 7 — three more `internal`s had to widen.** `formatPublishedTime`,
`isolateBidi` and `ImageryScrim` all had callers in screens that stay in
`:composeApp` until phase 9. Same pattern as phase 6; the `internal` grep before
moving is now the standing first step of any extraction.

**Phase 8 — the tab finally moved, four phases after it was deferred.** Phase 2
left `currentTab` in `FeedViewModel` because switching tabs both moves the reader
and decides which feed loads, and splitting those without a shared object would
only have replaced the coupling with an event bus. `Navigator` is that object.

`Navigator` needs **two** members for the tab, not one. `tab` is a `StateFlow` —
where the reader is — and a `StateFlow` cannot express "the reader asked for the
tab they are already on", which is a real gesture: it means refresh. So
`tabSelections` is a separate `SharedFlow` that emits on every tap.

**`tabSelections` must not have replay.** The first implementation used
`replay = 1` so a selection could not be missed by a late collector. But a
replayed selection arrives at a freshly built collector that has already read
`tab`, which is exactly the same-tab shape — so every ViewModel rebuild would
fire a network refresh nobody asked for. Harmless while the ViewModels are Koin
singletons; phase 10 makes rebuilds routine, which is the whole reason this phase
exists. Now `replay = 0` with `DROP_OLDEST`, and a test pins it.

The same flow also had `check(tryEmit(...))`, which turns a full buffer into a
crash on a tab tap. `DROP_OLDEST` removes the failure mode: for a gesture, the
newest is the one that matters.

**Phase 8 — `close()` versus `close(overlay)`.** `AuthUiEffect.CloseOverlay` meant
"close the sign-in overlay", but `navigator.close()` means "pop whatever is on
top". Those differ the moment anything can be pushed above sign-in, and the
result would be the reader staring at a form they had already completed. The
navigator has a targeted `close(overlay)` for that, and auth uses it.

**Phase 8 — a test that stores what it then looks for.** `rememberAndFind` both
writes and reads. A test asserting "a country feed is not remembered" failed
because its own lookup call stored the very feed it went on to find. The lookup
has to be made from a different state. Worth remembering for any read-through
cache with this shape.

**Phase 8 — the same-tab refresh could not be observed, on this branch or on
`master`.** Scrolling three articles deep and tapping the active tab changes
nothing visible on either build — compared pixel by pixel, `master` changed 0.
So the refactor preserves the behaviour exactly, but whether the behaviour its
comment describes ("a refresh replaces the feed and the pager follows
`feedRevision` to the top") actually reaches the screen is an open pre-existing
question, not something this phase changed. There is no `FeedViewModel` test
fixture to settle it — the class has around a dozen dependencies. Worth building
one in phase 9, when the feed becomes its own module.

**Phase 9 — the plan contradicts itself on two screens.** It says "no feature
depends on another feature" and then places `ProfileScreen` in
`:feature:settings` — but that screen is a menu over auth, saved *and* feed, so
as a feature module it is three forbidden edges. It stays in `:composeApp`, for
exactly the reason the plan already gives for keeping onboarding there: it is a
shell route. `ArticleDetailsScreen` did move into `:feature:feed`, after its
`onShellEvent`/`onSavedEvent` parameters became four plain callbacks.

`SavedArticleCard` and `EmptySavedArticlesCard` went to `:core:ui`, not
`:feature:saved`, because `:feature:search` draws them too — and search
depending on saved is the exact edge this phase exists to forbid. When two
features want the same component, it belongs below both of them, not in
whichever one seems to own it.

Every cross-feature edge turned out to be the same shape: a screen taking
another feature's `UiState` or `UiEvent` so the shell could wire it up. The cure
is always plain parameters and callbacks, with the shell translating at the call
site — `OverlayHost` is where knowing that a saved-article tap carries
`ArticleOpenOrigin.SAVED` actually belongs.

**Phase 9 — the build ran out of memory three times, and the third was my own
fault.** None of it was a code error.

- `configureNewsshortsKmpTargets` called `binaries.executable()` for every
  module, so twelve libraries were linking production JS and Wasm bundles that
  nothing loads. Executables are opt-in now, and only `newsshorts.kmp.app` opts
  in. This was the phase 4 fix for `:core:contract`, which should have been
  generalised then. Five failures became three, and 40 minutes became 17.
- The rest were `compileTestDevelopmentExecutableKotlin{Js,WasmJs}` — *test*
  bundle links, which that flag does not govern.
- **Raising `kotlin.daemon.jvmargs` to 6.5 GB made it worse.** On a 16 GB
  machine also running Android Studio and an emulator, that heap does not exist:
  free memory fell to 0.7 GB and a task that fails in ten minutes ground for an
  hour and 38 instead. Memory settings here are a budget, not a wish.

The fix is fewer heavy tasks at once, not a bigger heap. `LinkTaskThrottle` in
build-logic is a shared build service with `maxParallelUsages = 1` wired to the
JS and Wasm executable link tasks, so those queue while everything else still
runs in parallel. Measured: serialised wasm tests finish in 1m34 where the
parallel run failed after 5m19. A forced cold relink of both targets with
`--rerun-tasks --no-build-cache` now passes in 5m40 with zero
`OutOfMemoryError`.

Note what was *not* done: dropping the JS or Wasm targets would have fixed the
memory problem by deleting test coverage on two of the six platforms.

**Phase 10 — `viewModel { }` needed `BaseViewModel` to stop being an `expect`
class, and Android could not have told us.** The switch compiled and ran fine on
Android, then failed on iOS, JS and Wasm with `Return type mismatch: expected
'ViewModel', actual 'FeedViewModel'`. The DSL requires `T : androidx ViewModel`,
and `BaseViewModel`'s actual only extended one on Android — everywhere else it
was a plain class.

`lifecycle-viewmodel` is multiplatform now
(`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel`), so `BaseViewModel` is
a single common class over `androidx.lifecycle.ViewModel` and the five actuals
are gone. That also settles a real inconsistency the plan noticed: Android
deliberately never cancelled its scope while iOS, JS and Wasm always did. Now
`androidx.lifecycle.viewModelScope` is cleared by the same rule on all six.

The lesson for the phases that remain: **an `expect`/`actual` class is a place
where platforms are allowed to disagree about a type's supertype**, so any
library API with a bound on that type will compile on the platform whose actual
satisfies it and fail on the rest. Build Android first for speed, but never read
Android green as the phase being green.

**Phase 10 — the scope leak is gone.** `BaseViewModel.android.kt` used to
document why it must *not* cancel: the definitions were `single`, so one
instance served the process, the first Activity destruction called `onCleared`,
and Koin then handed the same dead-scoped instance back with no second `init` —
every coroutine in the app stopped after the reader backed out once. Not
cancelling fixed the symptom and leaked the scope for the process lifetime.
With `viewModel { }` the store hands back a fresh instance, so cancelling is
correct again.

What made this safe is everything from phases 1-8: no ViewModel owns state that
has to outlive it any more — `SettingsManager`, the saved-articles repository,
`AuthSession`, `Navigator` and the buses are all singletons. Verified on device:
back out to the launcher, return, switch tabs — fresh content loads, so the
scope is alive; and the selected tab survives a rotation, because the
`Navigator` owns it rather than the ViewModel.

**Phase 10 — `viewModel { }` definitions resolve through a plain `get()`.**
Worth knowing, because iOS, JS and Wasm retrieve with
`remember { KoinPlatform.getKoin().get<T>() }` rather than `koinViewModel()`. A
throwaway JVM probe confirmed it before the switch was made rather than after.
Each `provideXViewModel` is called from exactly one composable, so the
factory-like scoping does not split an instance in two.

**Phase 10 — where the plan's `provideXViewModel` placement cannot work.** It
asks for them in `:core:ui`, but each returns a feature's ViewModel type, and
`:core:ui` sits below the features — the dependency would be a cycle. They stay
in `:composeApp`, which is the composition root and the one module that can
name every feature.

**Phase 11 — `check` no longer needs `-x lintDebug`.** Since phase 0 every phase
ran with lint switched off because of four `RestrictedApi` errors in
`TopStoryWidget`, which meant no lint regression anywhere in the project could
be caught for the whole migration.

The fix is a scoped `@SuppressLint("RestrictedApi")`, not a rewrite. I first
tried replacing `ColorProvider(R.color.…)` with a public day/night overload —
**that overload does not exist.** Glance 1.2.0 offers only `ColorProvider(Color)`
and the restricted `ColorProvider(resId)`; checking `javap` on the actual
artifact settled it after the compiler rejected the guess. Resolving
`values/` against `values-night/` in app code instead would move the day/night
decision out of the resource system and evaluate it in the app's configuration
rather than the host launcher's, which is a behaviour change to avoid for a
lint warning. The suppression is on the one composable, so anything else
restricted still fails.

Turning lint on for every module then surfaced a second error, and it is an
artifact of the split: `MissingPermission` on `FirebaseAnalytics.getInstance` in
`:core:data`, because lint checks a library's manifest in isolation and a
library has none. All three permissions are in the app's merged manifest —
INTERNET from `:composeApp`, `ACCESS_NETWORK_STATE` and `WAKE_LOCK` merged in by
firebase-analytics itself. Verified against `processDebugMainManifest` output
before suppressing, not assumed.

**Phase 11 — `isDebugBuild()` now fails closed.** The jvm, ios, js and wasmJs
actuals returned a hardcoded `true`, justified by "no shipped build of this
target exists yet" — which stays true right up until one ships, and nothing
would fail to say so. `AppGateViewModel` skips the device-integrity check when
this is true, so the flag is dangerous in exactly one direction.

iOS asks Kotlin/Native (`Platform.isDebugBinary`), which actually knows. The
others default to release; the JVM desktop build opts into debug through a
system property, because a property nobody sets cannot be on by accident in a
distributed build.

**Phase 11 — two smaller ones.** `NewsMapper`'s parse fallback returned a
hardcoded December 2024 timestamp, so an article with an unparseable date was
dated to a fixed point in the past; it uses `currentTimeMillis()` from
`:core:model` now. And wasmJs settings persist: phase 6b named the in-memory
storage honestly, this gives the target `localStorage` like js, which needed
`kotlinx-browser` since wasm's stdlib does not carry `kotlinx.browser` the way
the JS one does.

**Not done, deliberately:** the per-feature `AppStrings` split, which the plan
gates on measured incremental build times, and moving effects to carry semantic
values instead of localized prose, which the plan calls its own project.

**A ViewModel's effects are dead until something collects them.** `NewsScreen`
collected the feed's, settings', saved's and inbox's `uiEffect` — and not the
shell's. `AppShellViewModel` owns sharing and opening an article's source, so
both went into a `Channel` nobody read: the share button did nothing on the feed
and in article details, and neither did "read from source". Broken since phase 2
split the shell out of the feed, and shipped through nine further phases.

Nothing catches this. The `when` inside each collector is exhaustive, so a *new*
effect type on an already-collected flow fails the build — but a flow with no
collector at all compiles, runs, and silently drops everything. The audit worth
repeating: list every `val uiEffect` and match each to a `.uiEffect.collect`.
Six declared, six collected, is the state now.

**Smoke tests have to press the buttons.** Every phase here was checked on the
emulator, and the check was "does the screen render and is logcat clean". A
button wired to nothing renders perfectly and logs nothing. Rendering proves
composition; only pressing proves wiring — and for anything that leaves the app,
`adb logcat | grep "START u0 {act="` shows whether the intent actually fired,
which a screenshot cannot.
