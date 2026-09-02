# Modularizing News Shorts into feature modules

*English on purpose: this is a spec that later sessions execute, and it names
Gradle paths, Kotlin identifiers and shell commands. Chat stays Arabic.*

**Status:** reviewed twice — by a Plan agent and by Codex, both against the real
code. Their corrections are folded in. See *Review corrections* at the end for
what changed and why, so nobody re-proposes a rejected idea.

---

## Context

`composeApp` is one Gradle module holding 101 files / 13,308 lines of
`commonMain` plus 4,137 lines of `commonTest`, built for six targets (android,
iosArm64, iosSimulatorArm64, jvm, js, wasmJs). Models, Ktor client, local
stores, design system, a 145-member string table and every screen compile as one
unit. Any edit rebuilds all of it, nothing is testable in isolation, and no
structural barrier stops the next feature reaching into the last one.

Three concrete symptoms:

- **`NewsViewModel` is 1,476 lines with 19 constructor dependencies**, two of
  which are *other ViewModels* (`SavedArticlesViewModel`, `SettingsViewModel`,
  `NewsViewModel.kt:216`). Its ~60 handlers serve 13 unrelated concerns.
- **`NewsUiEvent` is a 45-case sealed interface** imported by ten UI files plus
  the ViewModel. Any feature module taking `(NewsUiEvent) -> Unit` transitively
  needs every other feature's event types — which is why a plain file move
  produces modules that are not actually independent.
- **Three package cycles** (`domain ↔ feature.search`,
  `domain → sync → data → domain`, `data.local ↔ data.remote`) plus a
  `data → presentation` edge. Invisible today; hard Gradle failures the moment
  those packages become modules.

Separately, `:server` and `:composeApp` hand-maintain seven duplicated
type/algorithm families, kept in step only by deliberately duplicated test
literals.

**Outcome wanted:** a strictly acyclic module graph where a feature is a real
unit — own ViewModel, own state, own events, own tests — buildable and testable
without the rest of the app, with the layering enforced by the compiler instead
of by discipline.

**Where this sits in the roadmap:** architectural work, not one of the numbered
product phases (1–4 done, 5 = AdMob, 6 = Play publishing). Do it **before
Phase 5** — ads land inside the feed UI, and doing them into a 1,476-line
ViewModel makes both jobs worse.

### Decisions already taken (do not re-open)

| # | Decision |
|---|---|
| 1 | Full split: `:core:*` + `:feature:*` + app. Not a single `:shared`. |
| 2 | `NewsViewModel` gets decomposed into per-feature ViewModels + a thin shell. |
| 3 | Navigation stays hand-rolled — no androidx.navigation, no Decompose — behind a `Navigator` interface in `:core:navigation`. |
| 4 | A shared `:core:contract` module is created and `:server` consumes it. |

---

## The ordering principle

**All semantic refactoring happens first, inside `:composeApp`. All Gradle
splitting happens second.**

- **Phases 1–3 change Kotlin only.** No new Gradle projects. They carry every
  bit of design risk (ViewModel decomposition, cycle breaking) and *zero* build
  risk: `./gradlew check` runs exactly as today, and a bad phase is one
  `git revert`.
- **Phases 4–10 create modules** by moving files whose packages are already laid
  out along the final module boundaries and already acyclic. They carry build
  risk (Compose Resources on iOS/wasm, KMP variant resolution, the Docker
  deploy) and essentially zero semantic risk — each is "move these files, add a
  `build.gradle.kts`, fix imports".

The usual approach — create `:core:model`, then refactor things into it — makes
every failure ambiguous (design bug or Gradle bug?) and makes phases
non-revertible. This order is why the highest-risk phase (2) can come early and
risk still decreases over time.

---

## Target module graph

```
build-logic                  included build — convention plugins, not a dependency

:core:contract   → (kotlinx-serialization only)
:core:config     → (nothing; generated BuildConfig)
:core:model      → :core:contract
:core:domain     → :core:model
:core:localization → :core:model
:core:data       → :core:contract, :core:model, :core:domain, :core:config
:core:ui         → :core:model, :core:localization      (theme + components + resources
                                                          + BaseViewModel + VM providers)
:core:navigation → :core:model, :core:contract, :core:ui
:core:testing    → :core:domain, :core:model            [testImplementation only]

:feature:feed  :feature:saved  :feature:search
:feature:settings  :feature:auth  :feature:inbox
        → :core:{model,domain,localization,ui,navigation}  (+ :core:config for settings)

:composeApp      → all :core:*, all :feature:*
:server          → :core:contract      (JVM only, toolchain 17)
```

**15 new projects** (9 core incl. testing, 6 feature) plus `:composeApp` and
`:server`.

### Layering rule

> **Tier 0** — `:core:contract`, `:core:config` — depend on nothing in-repo.
> **Tier 1** — `:core:model`, `:core:localization` — may depend on tier 0.
> **Tier 2** — `:core:domain` — tiers 0–1.
> **Tier 3** — `:core:data`, `:core:ui` — tiers 0–2.
> **Tier 3b** — `:core:navigation` — tiers 0–2 **plus `:core:ui`** (the one
> sanctioned intra-tier edge, for `BottomNavigationBar`).
> **Tier 4** — `:feature:*` — tiers 0–3. **A feature may never depend on another
> feature.**
> **Tier 5** — `:composeApp` — the only module allowed to depend on tier 4.
> `:server` depends on `:core:contract` and nothing else.
> `:core:testing` is consumed only via `testImplementation`, depends on tiers 0–2.

Cross-feature communication happens through exactly three channels, all in
`:core:*`:

1. **Shared observable state** — `SettingsPersistence.preferences`,
   `SavedArticles.saved`, `AuthSession.user`. All are `StateFlow`s; the first
   two already exist.
2. **`Navigator`** (`:core:navigation`) — a feature names an `Overlay`, never
   another feature's screen.
3. **`FeedInvalidator`** — a feature signals "the feed is stale"; the feed
   decides what to do.

`:core:contract` sits at the very bottom precisely so `:server` — JVM-only,
toolchain 17, built in a container with **no Android SDK** — can consume it
without pulling in a single client concern. It therefore holds no coroutines,
no Ktor, no Compose.

---

## Phase 0 — build-logic, convention plugins, cache-correct BuildConfig

**Goal:** stand up convention plugins and prove them against the existing
`:composeApp` before any module exists. Nothing moves.

### Included build, not `buildSrc`

`buildSrc` is an implicit dependency of every project: one edit invalidates the
whole configuration cache (`org.gradle.configuration-cache=true` is already on)
and recompiles everything. Worse, `server/deploy/settings.gradle.kts` is a
**second settings file** used by `server/Dockerfile`; a composite build can be
`includeBuild(...)`'d from both, `buildSrc` cannot.

```
build-logic/
  settings.gradle.kts     # pluginManagement repos + versionCatalogs {
                          #   create("libs") { from(files("../gradle/libs.versions.toml")) } }
  convention/
    build.gradle.kts      # `kotlin-dsl`, jvmToolchain(17)
    src/main/kotlin/...
```

Root `settings.gradle.kts` gains `pluginManagement { includeBuild("build-logic") }`.

The catalog needs new `[libraries]` entries for the plugin **artifacts** — a
catalog `[plugins]` entry is not consumable as a classpath dependency:

```toml
android-gradlePlugin             = { module = "com.android.tools.build:gradle",                      version.ref = "agp" }
kotlin-gradlePlugin              = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin",           version.ref = "kotlin" }
compose-gradlePlugin             = { module = "org.jetbrains.compose:compose-gradle-plugin",         version.ref = "composeMultiplatform" }
composeCompiler-gradlePlugin     = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }
kotlinSerialization-gradlePlugin = { module = "org.jetbrains.kotlin:kotlin-serialization",           version.ref = "kotlin" }
```

### The plugins

| Plugin ID | Applies | Configures |
|---|---|---|
| `newsshorts.kmp.library` | `kotlin.multiplatform` + `com.android.library` | the 6 targets; `kotlin { compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") } }`; `androidTarget { compilerOptions { jvmTarget = JVM_11 } }`; `android { namespace derived; compileSdk/minSdk from catalog; compileOptions 11 }`; commonTest gets `kotlin-test` + `kotlinx-coroutines-test`; `check dependsOn iosSimulatorArm64Test` **guarded by `HostManager.hostIsMac`** |
| `newsshorts.kmp.compose` | above + compose + compose-compiler | the standard Compose commonMain set + `compose-ui-backhandler` |
| `newsshorts.kmp.feature` | above | the standard feature edges (`api(projects.core.model)`, `implementation` on domain/localization/ui/navigation, koin, coroutines) + `testImplementation(projects.core.testing)` |
| `newsshorts.kmp.contract` | `kotlin.multiplatform` + serialization | **target set gated by a Gradle property** (below); serialization-json only |
| `newsshorts.kmp.app` | `kotlin.multiplatform` + compose + serialization | same 6 targets and compiler args, **without** `com.android.library` — `:composeApp` keeps its own `com.android.application` block inline |
| `newsshorts.buildconfig` | — | registers `GenerateBuildConfig` and wires its output into `commonMain` |

**Namespace derivation:** `com.mk.newsshorts` + project path with `:` → `.`.
`:core:model` → `com.mk.newsshorts.core.model`. Android namespaces need not
match Kotlin packages, and `android.nonTransitiveRClass=true` is already set.

**`-Xexpect-actual-classes`:** replace today's
`targets.all { compilations.all { compileTaskProvider.configure {...} } }`
(`composeApp/build.gradle.kts:135-143`) with the KGP 2.x top-level
`kotlin { compilerOptions { ... } }`. It propagates to every target, avoids
per-compilation task realization, and is configuration-cache friendly.

**Not moved into convention plugins, deliberately:** `:composeApp`'s
`com.android.application` block (signing, `staging` build type, manifest
placeholders, conditional Firebase plugins, `verifyReleaseSigning`) and
`compose.desktop`. They exist once; generalizing them buys nothing.

### `:core:contract` target gating — the most fragile build logic here

`server/Dockerfile` builds with `gradle:8.14-jdk17` on Linux with **no Android
SDK**, copying only `gradle/libs.versions.toml`,
`server/deploy/settings.gradle.kts` and `server/`. If `:server` depends on
`:core:contract`, that image must configure `:core:contract` — but the client
needs it to have `androidTarget()`, which needs AGP, which needs the SDK.

`newsshorts.kmp.contract` reads
`providers.gradleProperty("newsshorts.contract.targets").orElse("all")`. When
the value is `jvm`, only `jvm()` is configured and AGP is never applied:

```dockerfile
COPY gradle/libs.versions.toml gradle/libs.versions.toml
COPY build-logic build-logic
COPY core/contract core/contract
COPY server/deploy/settings.gradle.kts settings.gradle.kts
COPY server server
RUN gradle :server:installDist --no-daemon -Pnewsshorts.contract.targets=jvm
```

`server/deploy/settings.gradle.kts` gains
`pluginManagement { includeBuild("build-logic") }` and `include(":core:contract")`.

> **Documented fallback** if this misbehaves: have `:server` source-include the
> contract — `sourceSets.main.kotlin.srcDir("../core/contract/src/commonMain/kotlin")`.
> Ugly, keeps the Dockerfile a one-line change, still removes the duplication,
> which is the actual goal. **Its failure mode is "the deploy silently stops
> working", so it must be proven with a real `docker build` in Phase 4.**

### BuildConfig — fix a latent bug, do not carry it forward

Today `composeApp/build.gradle.kts:110-131` calls `buildConfigFile.writeText(...)`
at **configuration** time, into `src/commonMain`. With the configuration cache
on, a cache *hit* skips configuration entirely — so on every cached run the file
is simply whatever the last uncached run wrote. It works today only because the
stale file happens to be right. The roadmap memory already records the cost:
a changed `local.properties` leaves the installed APK on the old backend with
no error.

```kotlin
abstract class GenerateBuildConfig : DefaultTask() {
    @get:Input abstract val fields: MapProperty<String, String>
    @get:Input abstract val packageName: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @TaskAction fun generate() { /* write BuildConfig.kt */ }
}
```

- `local.properties` is read through a
  `LocalPropertiesValueSource : ValueSource<Map<String,String>, Params>` so
  Gradle registers it as a **tracked** configuration input and invalidates the
  CC entry when it changes. Today's `Properties().load(...)` is silently
  untracked.
- Output → `build/generated/buildconfig/commonMain/kotlin`, wired via
  `kotlin.sourceSets.commonMain.get().kotlin.srcDir(generateTask)` (passing the
  `TaskProvider` carries the dependency). The file leaves `src/`, so the
  `.gitignore` entry `**/config/BuildConfig.kt` goes.
- `appVersionCode`/`appVersionName` move from the build script
  (`composeApp/build.gradle.kts:107`) to root `gradle.properties` as
  `newsshorts.versionCode` / `newsshorts.versionName`, read via
  `providers.gradleProperty`, so `:composeApp`'s `android {}` and `:core:config`
  read one value without a project→project reference.
- Package stays `com.mk.newsshorts.config`, so no import changes anywhere.

### Catalog hygiene (same phase, mechanical)

Delete the dead entries: all six `sqldelight-*` plus the `sqldelight` plugin,
`datastore-preferences-core`, `junit`, `kotlin-testJunit`,
`androidx-testExt-junit`, `androidx-espresso-core`, `androidx-appcompat`,
`androidx-core-ktx`, `kotlinx-datetime`, and their `[versions]`. Add `[bundles]`
for the recurring groups (`compose-common`, `ktor-client-common`, `koin-common`)
so each convention plugin reads as one line.

**Verify:**

```bash
./gradlew clean build --configuration-cache
```

Run it twice — the second run must report a configuration-cache **hit**. Then:

```bash
./gradlew :composeApp:assembleDebug :composeApp:jvmTest :composeApp:iosSimulatorArm64Test
```

```bash
docker build -f server/Dockerfile .
```

Plus one Xcode build, and the stale-backend check: change `BACKEND_ORIGINS` in
`local.properties`, rebuild **without** `--no-configuration-cache`, confirm the
new origin ships.

**Risk:** Low — nothing moves; rollback is one revert. **Effort: M.**

---

## Phase 1 — Shared services; delete VM-in-VM injection (in place)

**Goal:** kill `NewsViewModel(savedArticlesViewModel, settingsViewModel)` by
moving shared state out of ViewModels into single-instance services, so no
ViewModel reads another ViewModel. No Gradle changes.

### The four cross-concern reads, and what serves each

| Read today | Written by | Read by | New source |
|---|---|---|---|
| `selectedLanguage`, `appLocale`, theme, text scale, categories | categories, sync, settings | feed, inbox, share, search, theme | **`SettingsPersistence.preferences: StateFlow<AppPreferences>`** — already exists on `SettingsManager`. Every VM injects the interface. This is also why `currentSyncedSettings():288` already deliberately reads the store, not the state. |
| `articles` | feed | deeplinks, analytics | **Nothing outside the feed reads it.** Deep links resolve through the cache (`ArticleLookup`, below); analytics events carry the article by value. |
| `authUser` | auth | sync, ProfileScreen, SettingsScreen | **New `AuthSession`** (`val user: StateFlow<AuthUser?>`) over `AuthClient`. Auth state becomes a repository, not a VM field. |
| `overlays` | nav | everyone | **`Navigator.overlays`** (Phase 2 / `:core:navigation`). |

### New abstractions

- **`AuthSession`** — interface + impl over `AuthClient`, eager `single`.
  Replaces `observeAuthState()` living in `NewsViewModel:272`.
- **`SyncPublisher`** — `publishSavedArticles(...)`, `publishSettings(...)`;
  holds `AuthSession` + `RemoteSyncClient` + its own scope; a no-op when signed
  out. Replaces `pushSavedArticlesIfSignedIn:318` / `pushSettingsIfSignedIn:323`
  and lets `SavedArticlesViewModel` and `SettingsViewModel` push directly.
  **This is why the five delegating settings handlers in `NewsViewModel` get
  deleted rather than moved** — they existed only to trigger a sync push.
- **`FeedInvalidator`** — `val signals: SharedFlow<InvalidationReason>`,
  `invalidate(reason)`. Reasons: `LanguageChanged`, `CountryChanged`,
  `SyncApplied`, `OnboardingFinished`. Replaces every "…then reload the feed"
  imperative tail.
- **`SavedArticles`** — domain interface over the concrete
  `SavedArticlesRepository` (`saved`, `load`, `toggle`, `remove`, `replaceAll`).
  Needed for testability and to break Cycle B.
- **`InboxReadMarker`** — one method, `markRead(articleUrl)`, implemented by
  `NotificationInboxStore`. Lets the deep-link path mark read without
  `:feature:inbox` on its dependency path.
- **`ArticleLookup`** — `suspend fun find(url): NewsArticle?`, implemented by
  `NewsLocalDataSource` over the cached feed. Lets the deep-link path do its
  "look in the current feed" step without reading a ViewModel.
- **`AccountSyncUseCase`** — replaces `AccountSyncCoordinator`. Restructured
  from "a class needing `viewModelScope` plus two VM callbacks" into
  `suspend operator fun invoke(): SyncOutcome`, returning
  `SyncOutcome(settings: SyncedSettings?, saved: List<NewsArticle>)`. The caller
  launches it. **This is what makes it registerable in Koin** — today it is in
  no Koin module at all, hand-built at `NewsViewModel:265` — and what dissolves
  Cycle B.

  > **It stays call-driven, not observer-driven.** An earlier draft made it a
  > `single` observing `SavedArticles.saved` / `SettingsPersistence.preferences`
  > / `AuthSession.user`. That was rejected: all three are `StateFlow`s and emit
  > their current value immediately, so on sign-in it would push local defaults
  > or an empty unloaded saved list before remote hydration decides whether the
  > server has data — defeating the deliberate load-gate at
  > `AccountSyncCoordinator.kt:62`. Remote settings are applied through six
  > separate persistence calls (`SettingsViewModel.kt:72`), so an observer would
  > see intermediate states and echo them back, breaking the remote-wins
  > no-echo test at `AccountSyncCoordinatorTest.kt:182`, the unavailable-server
  > invariant at `:166`, and the cold-start load gate at `:86`. A lazy `single`
  > with no explicit caller would also simply never be resolved and observe
  > nothing. Keep the explicit call; keep the existing conflated-writer ordering
  > (`AccountSyncCoordinator.kt:126`) and all 16 of its tests.

### Refactors in this phase

- `SettingsViewModel` writes settings and calls `SyncPublisher` itself.
  `NewsViewModel`'s `handleSelectThemeMode:1213`, `handleSelectTextScale:904`,
  `handleToggleNotificationsEnabled:1218`, `handleToggleNotificationTier:1225`
  and `handleSelectAppLocale:782` are **deleted**, and `NewsUiEvent` loses those
  five cases.
- `SavedArticlesViewModel` gains `strings: () -> AppStrings` (built from
  `SettingsPersistence`) and its own `SavedArticlesUiEffect.ShowToast`. The
  unchecked `as SavedArticlesMutation.Changed` cast at `NewsViewModel:1192`
  disappears — the mutation stops crossing a boundary.
- `applySyncedSettings:291` becomes three writes to three services:
  `settingsManager.apply(...)`, `savedArticles.replaceAll(...)`,
  `feedInvalidator.invalidate(SyncApplied)`.
- **Language change** becomes one write (`settingsManager.setNewsLanguage`) and
  two independent observers: `FeedViewModel` (clears `CategoryFeedMemory`,
  reloads) and a new `PushSubscriptionSynchronizer` single. *One writer, several
  reactors* — the pattern the whole decomposition rests on.

**Verify:** the existing 258 tests pass where the API is unchanged; new tests for
`SyncPublisher` (signed-out no-op), `AuthSession`, and `AccountSyncUseCase`
(needs no scope → trivially testable, unlike today). Manual: sign in → change
language → confirm push re-subscribe and feed reload; sign in on a second device
→ confirm settings and saved articles arrive.

**Risk:** Medium-high **semantic** risk, zero build risk. Sync and
push-subscription are the two behaviours most likely to regress silently. Land
`AuthSession` + `SyncPublisher` as commit 1 and `FeedInvalidator` + the
settings-observation rewiring as commit 2, so a bisect isolates them.
**Effort: L.**

---

## Phase 2 — Decompose `NewsViewModel` and `NewsUiState` (in place)

**Goal:** turn the 1,476-line ViewModel and 323-line state into seven focused
pairs, in packages named for their eventual modules
(`com/mk/newsshorts/feature/<name>/`, `com/mk/newsshorts/core/<name>/`) — the
exact paths Phases 5–9 lift out. Still no Gradle changes.

### The resulting ViewModels

| ViewModel | Future module | State | Dependencies | Absorbs |
|---|---|---|---|---|
| `FeedViewModel` | `:feature:feed` | `FeedUiState` — the 13 feed fields + `selectedCategory`, `categoryOrder`, `selectedCountry`, `isOfflineMode` | `FeedRepository`, `SeenArticlesStore`, `SettingsPersistence`, `FeedInvalidator`, `SavedArticles`, `AnalyticsReporter`, `Navigator`, `strings()` | the 13 feed/paging fns, `handleSelectCategory/Country/Language`, `handleScrollToArticle`, `reportArticleLeft`, `reportDepth`, `handleRefreshNews`, `handleRetryLoading/NextPage`, `handleNewsError`, `handleDismissError`, `handleShareArticle`, `handleOpenArticleSource`, `CategoryFeedMemory` |
| `SavedArticlesViewModel` | `:feature:saved` | unchanged | `SavedArticles`, `SyncPublisher`, `AnalyticsReporter`, `strings()` | `handleSaveArticle`, `handleRemoveSavedArticle` — **including the toast copy and the sync push** |
| `SearchViewModel` | `:feature:search` | unchanged | + `SettingsPersistence`, `Navigator` | nothing new — but it now reads language from settings and observes `Navigator`, which **deletes the cross-VM adapter at `NewsScreen.kt:135-156`** |
| `SettingsViewModel` | `:feature:settings` | + `authUser`, `authInProgress`, `authError` from `AuthSession` | + `SyncPublisher`, `AuthSession`, `Navigator` | the 5 delegating handlers are deleted, not moved |
| `AuthViewModel` **(new)** | `:feature:auth` | `AuthUiState` | `AuthClient`, `AuthSession`, `PendingSignInEmailStore`, `SignInLinkBus`, `DeleteAccountUseCase`, `AnalyticsReporter`, `Navigator`, `strings()` | all 9 auth handlers + `observeSignInLinks` + `observeAuthState` |
| `InboxViewModel` **(new)** | `:feature:inbox` | `InboxUiState` — the 4 inbox fields | `NotificationInboxClient`, `NotificationInboxStore`, `NotificationBus`, `SettingsPersistence`, `AnalyticsReporter`, `Navigator` | all 7 inbox handlers + `observeArrivingNotifications` |
| `OnboardingViewModel` **(new)** | `:composeApp` | `OnboardingUiState` | `SettingsPersistence`, `PushSubscriber`, `FeedInvalidator`, `AnalyticsReporter` | `handleOnboardingNext/Skip/ToggleCategory`, `finishOnboarding` |
| `AppShellViewModel` **(new)** | `:composeApp` | `AppShellUiState` — `requiredUpdate`, `securityNotice`, `securityReason` (`overlays`/`currentTab` come from `Navigator`) | `Navigator`, `RemoteConfigClient`, `DeviceIntegrityInspector`, `SettingsPersistence`, `AnalyticsReporter`, `DeepLinkBus`, `SharePageResolver`, `DeepLinkRouter`, `AccountSyncUseCase`, `AuthSession` | `checkForRequiredUpdate:472`, `handleDismissSecurityWarning:523`, `observeDeepLinks:530`, `handleOpenDeepLink:1136`, `handleOpenSharePage:1128`, `loadSavedSettings`, sync orchestration |

### The five tangled groups, untangled

**(a) Saved: toast + sync push + downcast.** `SavedArticlesViewModel` owns all
three. `SyncPublisher` gives it the push without `AuthClient`; injected
`strings()` gives it the copy; handling its own event end-to-end deletes the
cast. *Abstractions: `SyncPublisher` + injected strings.*

**(b) `applySyncedSettings`** (settings + saved + clear category cache +
reload). Becomes fan-out over services (see Phase 1). `FeedViewModel` observes
`FeedInvalidator` and clears its own `CategoryFeedMemory`.
*Abstractions: `AccountSyncUseCase` returning a value + `FeedInvalidator`.*

**(c) `finishOnboarding:860`** (categories + settings + notifications + feed).
Three writes to `SettingsPersistence`, plus `pushSubscriber.subscribe(...)`,
plus `feedInvalidator.invalidate(OnboardingFinished)`. `FeedViewModel` already
observes `preferences.categoryOrder`, so the category part needs no signal at
all. *Abstractions: none beyond (b).*

**(d) `handleOpenDeepLink:1136`** (feed → saved → payload → analytics → mark
read → open). Extract a **`DeepLinkRouter`** with an ordered resolver chain:

```
SavedArticlesResolver(SavedArticles) → CachedFeedResolver(ArticleLookup) → PayloadResolver(link)
```

All three depend only on domain interfaces. `AppShellViewModel` then does
`analytics.report(...)`, `inboxReadMarker.markRead(url)`,
`navigator.open(Overlay.ArticleDetails(article, origin))`. The "look in the
current feed" step reads the cache the feed came from, not a ViewModel.
*Abstractions: `DeepLinkRouter`, `ArticleLookup`, `InboxReadMarker`.*

**(e) Language change.** One write, two observers (Phase 1).
*Abstraction: `PushSubscriptionSynchronizer`.*

### `NewsUiState` decomposition

`NewsUiState.kt` disappears. Its **32 stored fields** (`:15`–`:127`) land in the
seven state classes above. The types it *also* declares relocate onto packages
that anticipate their modules:

| Type | New package → module |
|---|---|
| `Overlay:212`, `NavigationTab:276`, `ArticleOpenOrigin:266` | `core.navigation` → `:core:navigation` |
| `OnboardingStep:232` | `app.onboarding` → `:composeApp` |
| `ThemeMode:243`, `TextScale`, notification tiers | `core.model.settings` → `:core:model` |
| `ArticleDetails:260`, `LanguageOption:285`, `CountryOption:305`, `InboxNotification:188` | `core.model` → `:core:model` |
| `InboxReadState`, `articleKey` (today in `data/local`) | `core.model.inbox` → `:core:model` |

> `ArticleOpenOrigin` **must** move before Phase 8, because
> `Overlay.ArticleDetails` carries it and `:core:navigation` cannot compile
> otherwise.

### UI relocation (representative)

- `NewsScreen.kt` (603) splits three ways: shell/`Scaffold`/tab host →
  `composeApp/ui/AppScaffold.kt`; feed pager → `feature/feed/FeedScreen.kt`;
  the overlay `when` → `composeApp/ui/OverlayHost.kt`. **The cross-VM adapter at
  `:135-156` and one of the two effect collectors are deleted.**
- `ArticleDetailsScreen`, `NewsCard`, `FeedTextLimits` → `feature/feed/`
- `SignInScreen` → `feature/auth/`; `NotificationInboxScreen` → `feature/inbox/`
- `OnboardingScreen` → `composeApp/ui/onboarding/`
- **`components/ProfileScreen.kt` (434, misfiled — it is a screen) →
  `feature/settings/`**, taking `authUser: AuthUser?` as a parameter. It goes to
  settings, **not** auth: it imports `SavedArticlesUiState`
  (`ProfileScreen.kt:49,66`), so putting it in `:feature:auth` would create a
  forbidden `feature:auth → feature:saved` edge. As a settings screen it takes
  plain parameters from the shell instead.
- `SavedArticleCard`, `EmptySavedArticlesCard` → `feature/saved/`
- `BottomNavigationBar` → `core/navigation/ui/`
- `LicensesScreen`, `SplashScreen`, `BlockingNoticeScreen`,
  `SecurityWarningDialog` → `composeApp/ui/`
- everything else in `components/` + all of `theme/` → `core/ui/`

**Every feature screen takes `(State, (Event) -> Unit)`** — `SearchScreen.kt` is
the template. `OnboardingScreen` currently imports `SettingsUiState`
(`:35,:58`); it takes plain parameters instead, so `:composeApp`-hosted
onboarding never depends on `:feature:settings`.

**Verify:** the 258 tests, redistributed by subject; every new VM gets its own
test file. Manual pass over: cold start with a notification tap, share-link cold
start, sign-in-link cold start, onboarding, language switch, sign in / sync /
sign out / delete account, search, saved, inbox mark-read, security warning,
forced update.

**Risk:** the highest-risk phase in the plan — ~60 files, rewrites the app's
control flow. Split into **7 commits, one per ViewModel**, keeping
`NewsViewModel` as a thinning delegating shim until the last commit removes it.
The app runs at every commit and a behavioural regression is bisectable. If it
must span sessions, the natural cut is: commits 1–4 (`FeedViewModel`,
`AuthViewModel`, `InboxViewModel`, `OnboardingViewModel`) ship independently
safe; commits 5–7 (`AppShellViewModel`, `NewsUiState` dissolution, shim
deletion) must land together. **Effort: XL.**

---

## Phase 3 — Break the cycles (package moves only)

**Goal:** make the package graph strictly acyclic *before* Gradle can enforce
it, so Phases 5–9 are pure mechanics.

### Cycle A — `domain ↔ feature.search`

`domain/repository/NewsRepository.kt:6` imports `feature.search.SearchIndex`
(used at `:46`); back edge at `feature/search/SearchNewsUseCase.kt:5`.

- `feature/search/SearchIndex.kt`, `SearchText.kt` → `core/model/search/`
  (pure types over `NewsArticle`; the class comment already says so)
- `SearchNewsUseCase` + the `SearchNews` fun interface → `core/domain/search/`
- `RecentSearchesStore` → `core/data/local/`, behind a `RecentSearches`
  interface in `core/domain/`

`:feature:search` retains only `SearchViewModel` + `SearchScreen`.

### Cycle B — `domain → sync → data → domain`

- `RemoteSyncClient` interface → `core/domain/sync/`; `SyncedSettings`,
  `SyncFetch`, `SyncDelete` → `core/model/sync/`
- **`AppPreferences` + `readAppPreferences`** → `core/model/settings/` — **and
  `NotificationPreferenceKeys` moves with them**, because
  `readAppPreferences` reads it (`AppPreferences.kt:32`). Moving the decoder
  without the keys would create a fresh `core:model → core:data` edge.
  `SyncedSettingsMapping` follows to `core/model/sync/`.
- `AccountSyncCoordinator` is already gone (Phase 1), replaced by
  `AccountSyncUseCase` in `core/domain/sync/` depending only on interfaces.
- `DeleteAccountUseCase`'s `auth.*`/`sync.*` imports become intra-package.
- `ToggleResult` → `core/model/`

### Cycle C — `data.local ↔ data.remote`

**Correction to an earlier draft:** the cache does **not** serialize wire DTOs.
`NewsLocalDataSource` already has its own `@Serializable CachedArticle`
(`NewsLocalDataSource.kt:28`) and maps `ArticleDto` into it at `:117`. So:

- **No cache format migration. No schema bump. No purge.** The
  `NewsApiResponse`/`ArticleDto`/`SourceDto` family is an internal on-disk
  format; both halves land in the same `:core:data` module, so keeping it costs
  nothing and avoids a forced cache loss on every installed device. Only
  `BackendArticleDto`/`BackendFeedResponse` — the actual wire types — move to
  `:core:contract`, with `NewsMapper` translating contract → model.
- **`expect fun currentTimeMillis()`** is declared *inside*
  `NewsLocalDataSource.kt:277`, which is the whole reason `data.remote` imports
  `data.local`. Move it to `core/model/time/Time.kt` with its five actuals. The
  back edge disappears outright.
- `OriginPreferenceStore` interface → `core/domain/`;
  `SettingsOriginPreferenceStore` impl stays in `core/data/local/`.

### The `data → presentation` edge

- **`ArticleDeepLink` needs splitting, not moving.** It imports Ktor's `Url`
  (`ArticleDeepLink.kt:3`, used in parsing at `:54`) and both consumes and
  produces `NewsArticle` (`:151`, `:163`). `:core:contract` must stay Ktor-free
  and model-free, so:
  - `core/contract/` gets the pure part: scheme/host/param constants,
    `ShareSlug`, and slug/URL **formatting** implemented with plain string
    handling (no Ktor `Url`).
  - `core/model/deeplink/` gets `ArticleDeepLink` the data class, the Ktor-based
    `parse()`, `toNewsArticle()`, and `shareUrl(article)`.
  - Consequently `ArticleDeepLinkTest` **splits too** — it imports
    `ArticleDescription` (`:3`) and tests `toNewsArticle()` (`:78`), so its
    codec assertions go to `:core:contract` and its domain-adapter assertions to
    `:core:model`.
- `navigation/NotificationBus.kt:3` imports `presentation.mvi.InboxNotification`
  → `InboxNotification` moves to `core/model/inbox/` (Phase 2).
- `RemoteConfigClient`: `AppConfigDto` → `core/contract/`; `RequiredUpdate` +
  pure `requiredUpdateFor` → `core/model/`; interface → `core/domain/`; impl
  stays in `core/data/remote/`.
- `security/DeviceIntegrity.kt`: `IntegrityPolicy`, `SecurityNotice`,
  `SecurityReason`, pure `securityNoticeFor` → `core/model/security/`;
  `DeviceIntegrityInspector` interface → `core/domain/`.
- `auth/AuthClient.kt`: `AuthUser`, `AuthFailure`, `AuthResult` →
  `core/model/auth/`; interface → `core/domain/`.
- `analytics/AnalyticsReporter.kt`: the 16-subclass `AnalyticsEvent` →
  `core/model/analytics/`; interface → `core/domain/`.
- `notifications/PushSubscriber.kt`: interface → `core/domain/`.

### One Android-side edge that must be fixed here

`AndroidDeviceIntegrityInspector` imports the **AGP-generated** `BuildConfig`
(`DeviceIntegrityInspector.android.kt:9`) for the expected signing SHA and the
debug flag. AGP generates that class only in the application module, so moving
the implementation into `:core:data` fails to compile. **Inject both values** —
`expectedSigningSha256: String` and `isDebug: Boolean` — through the
constructor, supplied by `:composeApp`'s `androidPlatformModule`. Do it in this
phase, while the file is already being touched.

`NewsMessagingService` and `TopStoryWidget` stay in `:composeApp/androidMain`,
which depends on everything, so no cycle. `TopStoryWidget` resolving
`FeedRepository` from the Koin root is legitimate (the system instantiates it)
and needs no change.

### Enforcement

Add a `checkPackageLayering` task in build-logic (~80 lines: regex over
`import com.mk.newsshorts.*` per file, asserting a declared tier map) and wire it
into `check`. It is the only thing that stops Phase 3 silently regressing before
Phase 9 makes Gradle the enforcer. Retire it (or keep it as a fast pre-check)
once the modules exist.

**Verify:** `./gradlew check` plus `checkPackageLayering` reporting zero
violations. No behavioural change; existing tests pass with import fixes only.

**Risk:** Low — mechanical, compiler-checked. **Effort: M.**

---

## Phase 4 — `:core:contract`, `:core:testing`, and server de-duplication

**Goal:** one definition per wire type and per shared algorithm; and a shared
fakes module, which is needed **now** rather than at the end.

### `:core:testing` comes early, not last

`DeleteAccountUseCaseTest.kt:6`, `AccountSyncCoordinatorTest.kt:16` and
`SavedArticlesRepositoryTest.kt:15` already import the shared fakes. Those tests
move out in Phases 5–6, so the fakes must have a home before then.

- `:core:testing`, plugin `newsshorts.kmp.library`. Contents: `FakeAuthClient`
  (147), `FakeRemoteSyncClient` (115), `FakeSavedArticlesLocalStore` (28), plus
  the ad-hoc fakes worth sharing (`FakeAnalyticsReporter`, `FakePushSubscriber`,
  `FakeSettingsStorage`, `FakeNavigator`, article builders). Deps:
  `api(projects.core.domain)`, `api(projects.core.model)`, `api(libs.kotlin.test)`.
- **Its code lives in `commonMain`, not `commonTest`.** Gradle cannot express a
  dependency on another KMP project's test source set (`java-test-fixtures` does
  not support KMP). Consumers write `testImplementation(projects.core.testing)`.

### `:core:contract`

Plugin `newsshorts.kmp.contract`; deps: `kotlinx-serialization-json` only.

| Contract type | Replaces |
|---|---|
| `FeedArticleDto` | `server/model/Models.kt:6` **and** `data/remote/BackendDtos.kt:18` |
| `FeedResponse` | `Models.kt:19` **and** `BackendDtos.kt:7` |
| `SentNotification` + list wrapper | `server/push/SentNotification.kt:14,23` **and** `NotificationInboxClient.kt:43,33` |
| `object NewsCategories` | `Models.kt:58`; `:core:model`'s `NewsCategory` enum maps onto these ids |
| `object ShareSlug` | `server/share/ShareSlug.kt` **and** `navigation/ShareSlug.kt` |
| deep-link constants + slug/URL formatting | the pure half of `server/push/ArticleDeepLinks.kt` and `navigation/ArticleDeepLink.kt` (the Ktor/model half stays in `:core:model` — see Phase 3) |
| `AppConfigDto` | `RemoteConfigClient.kt` |

**Not moved:** `server/feed/FeedPaging.kt` and `domain/feed/FeedPaging.kt` are
*not* duplicates — same filename, different concerns (page-boundary anchoring vs
prefetch distance). Leave both.

**Build wiring:** `include(":core:contract")`;
`server/build.gradle.kts` gets `implementation(projects.core.contract)`. Set
`jvmToolchain(17)` on the contract's `jvm()` target so the variant matches
`:server` — do **not** lower `:server` to 11. Then the Docker and deploy-settings
changes from Phase 0.

**Tests:** the duplicated `ShareSlug` literal assertions in both suites collapse
to one suite in `:core:contract`. That loses a real safety net (the two suites
currently prove client and server agree) — but they now share the *code*, which
is a strictly stronger guarantee.

**Verify:**

```bash
./gradlew :server:test :server:installDist
```

```bash
docker build -f server/Dockerfile .
```

```bash
./gradlew :server:generateStaticFeed
```

Diff the produced JSON and slug paths against a pre-change run — **byte-identical
output is the acceptance criterion**, because a slug change breaks every share
link already in the wild.

**Risk:** Medium-high, concentrated entirely in the deploy path and the slug.
Mitigated by the byte-diff. Rollback restores the duplicated files.
**Effort: M.**

---

## Phase 5 — Extract `:core:config`, `:core:model`, `:core:domain`

Purely mechanical after Phase 3.

| Module | Plugin | Contents | Deps |
|---|---|---|---|
| `:core:config` | `kmp.library` + `buildconfig` | generated `BuildConfig` **only** | none |
| `:core:model` | `kmp.library` | `domain/model/*` (NewsArticle + 10 value classes, NewsSource, FeedPage, NewsResult, NewsError, NewsCategory, FeedLanguage); the 4 pure algorithm files (`FeedPaging`, `CategoryPreferences`, `SeenRanking`, `SavedArticlesMerge`); `SearchIndex`/`SearchText`; `AppPreferences` + `readAppPreferences` + `NotificationPreferenceKeys`; `SyncedSettings` + mapping; auth types; `AnalyticsEvent`; security types + `securityNoticeFor`; `RequiredUpdate` + `requiredUpdateFor`; settings enums; `ArticleDetails`/`LanguageOption`/`CountryOption`/`InboxNotification`/`InboxReadState`; `ToggleResult`; `ArticleDeepLink` + `parse` + `toNewsArticle`; `currentTimeMillis` + 5 actuals | `:core:contract`, coroutines, ktor-client-core *(for `Url` in the deep-link parser only)* |
| `:core:domain` | `kmp.library` | every interface (`FeedRepository`, `SavedArticles`, `AuthClient`, `AuthSession`, `RemoteSyncClient`, `SyncPublisher`, `FeedInvalidator`, `PushSubscriber`, `AnalyticsReporter`, `DeviceIntegrityInspector`, `RemoteConfigClient`, `SettingsPersistence`, `RecentSearches`, `OriginPreferenceStore`, `ArticleLookup`, `InboxReadMarker`) + every use case + `domainModule` | `:core:model`, coroutines, koin-core |

**Use-case convention.** Three use cases follow three conventions today. Settle
on **an interface named for the action + a single `suspend operator fun invoke`
+ a `…UseCase` implementation** — except `GetTopHeadlinesUseCase`, which has
three public methods (`execute`/`nextPage`/`getCached`) and is honestly a
repository facade: **rename it `FeedRepository`**, interface in `:core:domain`.
Do the rename here, while every call site is already being touched.

**Note on `:core:model` size.** It will be large — models, four pure algorithm
files, and a dozen types pulled out of `NewsUiState`, `AuthClient`,
`DeviceIntegrity`, `RemoteConfigClient`, `AnalyticsReporter`. That is correct
(all pure data, no dependencies) but it means every module recompiles when it
changes. Keep it disciplined: **types and pure functions only — no interfaces,
no coroutine machinery beyond `StateFlow` in signatures.**

**Verify:** `./gradlew build`; the ~10 pure-algorithm test files now live here
and must run under `iosSimulatorArm64Test`.

**Risk:** Low. **Effort: L** (volume, ~40 files, not difficulty).

---

## Phase 6 — Extract `:core:data`

One module, not three. `network`, `database` and `data` are deployed together,
total ~2,300 common lines, and splitting them would only re-expose Cycle C as a
Gradle failure for no benefit. Split later only if a build scan or a real
alternate implementation justifies it. Keep internal `remote/`, `local/`,
`mapper/`, `repository/` packages.

Plugin `newsshorts.kmp.library` + serialization. Contents: all of `data/`, the
`sync`/`auth`/`analytics`/`security`/`notifications` **implementations**, plus
`AuthSessionImpl`, `SyncPublisher` impl, `PushSubscriptionSynchronizer`,
`DeepLinkRouter` resolvers, and `dataModule`. Deps: `:core:contract`,
`:core:model`, `:core:domain`, `:core:config`, ktor client (core /
contentNegotiation / json / logging + the five platform engines in their source
sets), koin-core, coroutines. `androidMain` takes the Firebase BoM +
auth/firestore/messaging/analytics/crashlytics, credentials, google-id,
coroutines-play-services, koin-android.

### Two refactors that belong here

**1. `SettingsStorage`: `expect class` → `interface`.** The three shared fakes
exist only because `SettingsStorage` is an `expect class`
(`SettingsManager.kt:137`) with no test actual. A commonTest `actual` would also
collide with the per-target `actual`s that already exist in each target's main
source set — so that is not the fix. Make it an interface with five
implementations (`AndroidSettingsStorage`, `NsUserDefaultsSettingsStorage`,
`JavaPrefsSettingsStorage`, `LocalStorageSettingsStorage`,
`InMemorySettingsStorage`). This makes it fakeable, removes one expect/actual
pair, lets `InMemorySettingsStorage` move to `:core:testing`, **and puts a name
on the wasm bug** — today wasmJs's actual is an in-memory map, so nothing
persists on wasm; afterwards that is an explicitly-named class rather than a
surprise. **Keep the storage key strings byte-identical and add a test
asserting them** — this touches persisted data paths.

**2. Collapse the four byte-identical no-op platform modules (4 × 26 lines).**
Do **not** create a `nonAndroidMain` intermediate source set — the default
hierarchy template has no ios+jvm+js+wasm group and hand-wiring `dependsOn` is
fragile. Instead make the no-ops **plain common classes** in
`:core:data/commonMain` (`NoOpAnalyticsReporter`, `NoOpPushSubscriber`,
`NoOpAuthClient`, `NoOpRemoteSyncClient`, `NoOpDeviceIntegrityInspector`) bound
in the common `dataModule`; Android's `androidPlatformModule` *overrides* those
bindings (Koin 4 allows override by default). Android passes
`listOf(androidPlatformModule)`; the other four pass `emptyList()`.

> This only works because `SettingsStorage` became an interface in refactor 1 —
> it is the one binding that genuinely differs per platform (Android needs a
> `Context`, `PlatformModule.android.kt:17` / `SettingsStorage.android.kt:6`,
> while iOS constructs it with no arguments, `PlatformModule.ios.kt:16`). Do
> refactor 1 first.

Also flag but **do not bundle**: `isDebugBuild()`'s jvm/ios/js/wasmJs actuals
all hardcode `true`, so release web and desktop builds run with Ktor body
logging on. Separate commit.

**Verify:** `./gradlew build` on all targets; the ~12 data test files run here;
`jvmTest/FeedOriginOutageIntegrationTest` (real local HTTP server) moves to
`:core:data/jvmTest`. Manual: settings persist across restart on Android, iOS
and desktop.

**Risk:** Medium — largest module, most platform source sets; the failure mode
is a missing engine dependency in one target, which the build catches. The
`SettingsStorage` change is the part that can lose user data if a key drifts.
**Effort: L.**

---

## Phase 7 — Extract `:core:localization` and `:core:ui`

### `:core:localization`

Plugin `newsshorts.kmp.compose` (it needs `CompositionLocal`). Contents:
`AppStrings.kt` (711), `LocaleProvider.kt` (57), `LocalizedLinks.kt` (20). Deps:
`:core:model`, compose runtime.

**`AppStrings` stays whole. Do not split it, and do not move it to Compose
Resources.**

- *Why not Compose Resources:* `stringResource()` is `@Composable` and selects
  its locale from the **system** locale, but this app has an in-app language
  switcher whose value lives in `SettingsManager` — so it would need a custom
  `ResourceEnvironment`. Strings are also read **outside** composition
  (`NewsViewModel.kt:248`, `SettingsViewModel.kt:116`), where the resources API
  is `suspend getString(...)`. And the current interface is compile-time
  exhaustive: adding a member breaks both `EnglishStrings` and `ArabicStrings`
  until translated. Resources give that up for nothing this app needs.
- *Why not split per feature now:* a shared interface in a `core:` module
  creates **no** feature-to-feature dependency — the cost is rebuild surface and
  discoverability, not architectural illegality. Splitting 145 members produces
  ~8 contracts, 16 implementation objects, provider plumbing, and ambiguous
  ownership of common words. Defer it; do not pay speculatively.
- *If it later becomes worth it* (measure incremental build times first), the
  shape is: `CommonStrings` + `AppLocale` + `LocalAppLocale` stay in
  `:core:localization`; each feature declares `FeatureStrings` + two objects +
  its own `CompositionLocal` + a `featureStrings(locale)` free function, driven
  by `LocalAppLocale`.
- *ViewModel-side reads* keep the free function `getStrings(locale): AppStrings`.
  Each VM that needs copy injects `SettingsPersistence` and computes
  `getStrings(preferences.value.appLocale)`. **That is what replaces
  `settingsViewModel.uiState.value.appLocale` at `NewsViewModel:249`.**

> The deeper problem — ViewModels producing localized prose at all — is real but
> is its own project: change effects to carry semantic values and localize in
> the UI. Not part of modularization.

### `:core:ui`

Plugin `newsshorts.kmp.compose`. Merges what an earlier draft had as two
modules (`:core:designsystem` + `:core:ui`); there is no second consumer that
justifies two artifacts. Contents:

- all of `presentation/ui/theme/` (543 lines, including the two `expect fun`s —
  which is exactly why this must be a KMP module and not a plain JVM one)
- the generic components: `AppButton`, `SectionHeader`, `LoadingScreen`,
  `ErrorScreen`, `OverlayTopBar`, `FilterPill`, `CategoryRow`, `CountrySelector`,
  `TimeFormatting`, `FeedTextLimits`
- `BaseViewModel` (expect + 5 actuals) and the `provideXViewModel` expect/actual
  composables

Deps: `:core:localization`, `:core:model`, compose,
`compose.components.resources`.

**It owns `composeResources/`** — the 11 TTFs and 3 drawables move wholesale
from `composeApp/src/commonMain/composeResources/`, and `:composeApp`'s own
resources config is removed so it stops generating an empty `Res`.

**`publicResClass`: keep `false` (the default).** Only two call sites exist:
`Typography.kt` (all 11 fonts) — which moves *into* `:core:ui` and keeps using
the internal `Res` — and `App.kt:19,60` (`Res.drawable.logo`), for which
`:core:ui` exposes `@Composable fun appLogoPainter(): Painter`. That keeps `Res`
an implementation detail and avoids two generated `Res` classes competing on the
classpath. Set
`packageOfResClass = "com.mk.newsshorts.core.ui.resources"` for clarity.

> Note: cross-module access to an `internal` generated `Res` is a **compile
> error**, not a silent missing font. The silent failure mode is different —
> a resource that compiles but is not packaged for a given target.

**Verify:** a green build proves the least here. Smoke-test resource packaging
*from a dependency module* on every target and **visually confirm the
Poppins/Tajawal fonts render and the splash logo appears**, in English and in
Arabic (Tajawal + RTL):

```bash
./gradlew :composeApp:installDebug
```

```bash
./gradlew :composeApp:run
```

```bash
./gradlew :composeApp:jsBrowserRun
```

```bash
./gradlew :composeApp:wasmJsBrowserRun
```

Plus an iOS simulator run.

**Risk:** Medium — low semantic risk, real packaging risk on iOS and wasm. Own
commit, with the five-target smoke test named in the commit message.
**Effort: M.**

---

## Phase 8 — `:core:navigation` and the `Navigator`

Plugin `newsshorts.kmp.compose`. Deps: `:core:model`, `:core:contract`,
`:core:ui` (for `BottomNavigationBar`), koin-core, coroutines.

```kotlin
enum class NavigationTab { FOR_YOU, COUNTRIES, PROFILE }

sealed interface Overlay {
    data class ArticleDetails(val article: NewsArticle, val origin: ArticleOpenOrigin) : Overlay
    data object Settings : Overlay
    data object SavedArticles : Overlay
    data object SignIn : Overlay
    data object Search : Overlay
    data object NotificationInbox : Overlay
    data object Licenses : Overlay
}

interface Navigator {
    val tab: StateFlow<NavigationTab>
    val overlays: StateFlow<List<Overlay>>
    fun selectTab(tab: NavigationTab)
    fun open(overlay: Overlay)
    fun close()
    fun handleBack(): Boolean   // true if consumed
}
```

- **Implemented once** by `OverlayNavigator`, a plain class registered as a Koin
  `single`. It holds the stack `NewsUiState.overlays` holds today, so the stack
  **survives ViewModel lifetime** — a prerequisite for Phase 10's `viewModel { }`
  switch.
- **A feature opens another feature's screen** by naming an `Overlay`.
  `:core:navigation` owns the *vocabulary*; `:composeApp/ui/OverlayHost.kt` owns
  the *binding* — one exhaustive `when` mapping `Overlay` → composable. Neither
  feature knows the other exists, and the compiler enforces the `when`.
- `Overlay.ArticleDetails` carries a `NewsArticle`, so
  `:core:navigation → :core:model`. Fine (tier 1), and it is what lets the
  deep-link router hand a resolved article straight to the navigator.
- **Back handling:** `OverlayHost` wraps
  `BackHandler(enabled = overlays.isNotEmpty()) { navigator.close() }` using the
  already-present `compose-ui-backhandler`, plus the secondary-tab rule from
  `NewsScreen.kt:191`. Per-feature back semantics come from *observing* the
  navigator: `SearchViewModel` resets when `Overlay.Search` leaves the stack.
  **This is what deletes the cross-VM adapter at `NewsScreen.kt:135-156`.**
- **The three buses** (`DeepLinkBus`, `NotificationBus`, `SignInLinkBus`) move
  here. They are platform→app inboxes rather than navigation, but they are
  consumed only by `:composeApp`, `:feature:auth` and `:feature:inbox`, and this
  is the lowest tier all three share; a `:core:eventbus` for three files is not
  worth a module. Note the naming smell and move on.

  > **Correction to the code as it stands:** the comment at `DataModule.kt:29`
  > says these are not lazy, but they are registered with a plain `single {}`
  > (`DataModule.kt:30-32`), which **is** lazy in Koin. Cold-start deep-link
  > ordering depends on them existing before anything resolves them, so register
  > them `single(createdAtStart = true)` in `navigationModule` and fix the
  > comment. This is a latent cold-start bug being carried, not a property being
  > preserved.

**Verify:** `./gradlew build`; navigation/deeplink/slug tests land here. Manual:
notification-tap cold start, `newsshorts://` cold start, share-page cold start,
sign-in-link cold start, and Android system back from every overlay depth.

**Risk:** Low-medium; cold-start ordering is the fragile part. **Effort: M.**

---

## Phase 9 — Extract the six `:feature:*` modules

Mechanical, because Phase 2 already placed the files under
`com/mk/newsshorts/feature/<name>/`.

| Module | Contents | Extra deps |
|---|---|---|
| `:feature:feed` | `FeedViewModel`, `FeedUiState/Event/Effect`, `CategoryFeedMemory`, `FeedScreen`, `NewsCard`, `ArticleDetailsScreen`, `FeedTextLimits`, `feedModule` | — |
| `:feature:saved` | `SavedArticlesViewModel`, `SavedArticlesScreen`, `SavedArticleCard`, `EmptySavedArticlesCard`, `savedModule` | — |
| `:feature:search` | `SearchViewModel`, `SearchScreen`, `searchModule` | — |
| `:feature:settings` | `SettingsViewModel`, `SettingsScreen`, `ProfileScreen`, `settingsModule` | `:core:config` (privacy-policy URL) |
| `:feature:auth` | `AuthViewModel`, `SignInScreen`, `authModule` | — |
| `:feature:inbox` | `InboxViewModel`, `NotificationInboxScreen`, `inboxModule` | — |

All six get `newsshorts.kmp.feature`. **No feature depends on another feature** —
Gradle now enforces what `checkPackageLayering` enforced by convention.

Two placement decisions, both driven by real edges found in review:

- **`ProfileScreen` goes to `:feature:settings`, not `:feature:auth`** — it
  imports `SavedArticlesUiState` (`ProfileScreen.kt:49,66`), and the Profile tab
  is a menu that navigates to Settings, Saved and Sign-in while reading exactly
  one auth field. A module for one 434-line screen is not worth its
  `build.gradle.kts`.
- **Onboarding stays in `:composeApp`.** It is a first-run *shell route*:
  `App.kt` already owns the Crossfade that selects it, it is entered from
  nowhere else, and it currently imports `SettingsUiState`
  (`OnboardingScreen.kt:35,58`) — which as a shell screen taking plain
  parameters is fine, and as a `:feature:onboarding` module would be a forbidden
  cross-feature edge.

**Verify:** `./gradlew build`; the ~10 ViewModel test files land in their
features and each runs `iosSimulatorArm64Test`.

**Risk:** Low. **Effort: M.**

---

## Phase 10 — Slim `:composeApp`, restructure DI, switch to `viewModel { }`

**`:composeApp` retains:** `App.kt`, `AppShellViewModel`, `OnboardingViewModel` +
screen, `ui/AppScaffold.kt`, `ui/OverlayHost.kt`, `SplashScreen`,
`BlockingNoticeScreen`, `SecurityWarningDialog`, `LicensesScreen`, the five
platform entry points, androidMain's `MainActivity` / `NewsMessagingService` /
`TopStoryWidget`, and `di/AppModules.kt`.

`App.kt`'s five-way Crossfade route stays. Its `barsUseDarkIcons` computation
(`App.kt:77-87`) now reads from `Navigator.overlays`, `Navigator.tab`,
`AppShellUiState` and `SettingsUiState` rather than one god state — extract it
as a pure `fun barsUseDarkIcons(...): Boolean` in `:core:ui` so it is
unit-testable.

### DI aggregation

Each module ships `com.mk.newsshorts.<module>.di.<name>Module`:

```kotlin
val appModules = listOf(
    configModule, dataModule, domainModule, navigationModule,
    feedModule, savedModule, searchModule, settingsModule, authModule, inboxModule,
    appShellModule,
)
```

`initializeKoin(platformModules, appDeclaration)` moves from `di/AppModule.kt`
up into `:composeApp` — it is the composition root and belongs at the top. The
five entry points keep calling it unchanged.

### `single` → `viewModel { }`

**Do the switch, here, as its own commit.**

The documented bug — "everything stopped after backing out once", worked around
by *not* cancelling `viewModelScope` in `BaseViewModel.android.kt:25-29` — is a
direct consequence of `single` + androidx `ViewModel`. Because the VM is
process-wide, the *first* Activity destruction clears the store and calls
`onCleared()`, cancelling a scope the *next* Activity's VM (same instance) still
needs. Not cancelling fixes the symptom and leaks the scope for the process
lifetime. Meanwhile iOS/JS/wasm actuals *do* cancel
(`BaseViewModel.ios.kt:12`) — so lifetime semantics are already inconsistent
across platforms.

With `viewModel { }` the VM is scoped to the composable's `ViewModelStoreOwner`,
a fresh instance is created after the store clears, and cancelling on clear is
correct. Rotation stays safe (the owner is the Activity, retained across
configuration change).

**But the real fix is architectural and already landed in Phases 1–8:** all
long-lived state now lives in `single` services — `SettingsManager`,
`SavedArticlesRepository`, `AuthSession`, `Navigator`, the three buses,
`NewsLocalDataSource`. Once no ViewModel owns state that must outlive it,
ViewModel lifetime stops mattering and the switch becomes safe rather than
merely correct-on-paper. **This is why it must not be done earlier.**

Two constraints:

- `koin-compose-viewmodel` is deliberately excluded on iOS Native
  (`composeApp/build.gradle.kts:230-231`), and js/wasm do not use it either. The
  `provideXViewModel` composables therefore **stay `expect`/`actual`** — one per
  feature, in `:core:ui`. android/jvm → `koinViewModel()`
  (`ViewModelProvider.android.kt:8`); ios/js/wasm →
  `remember { KoinPlatform.getKoin().get<T>() }` (`ViewModelProvider.ios.kt:11`).
  A single common `koinVM` helper would change ownership semantics on three
  platforms; do not write one.
- `TopStoryWidget` resolves `FeedRepository` and `NewsMessagingService` resolves
  the buses, both from the Koin root. Neither resolves a ViewModel, so the
  switch does not affect them.

**Verify:** `./gradlew build`; a full manual pass; and specifically — background
the app, let Android kill the Activity, return: feed, saved list, settings and
overlay stack must all be intact. Rotate on every screen. Confirm no scope leak
with a debug log in `onCleared`.

**Risk:** Medium. The `viewModel { }` switch is the one change here that can
regress state retention. Land it separately from the DI aggregation so it can be
reverted alone. **Effort: M.**

---

## Phase 11 — Optional cleanups

Each independent; none blocks anything.

- **`isDebugBuild()` actuals** hardcode `true` on jvm/ios/js/wasmJs, so release
  web and desktop run with debug logging and debug-only paths. A latent
  production issue, not a modularization one. **S.**
- **`NewsMapper`'s `getCurrentTimeMillis()` fallback returns a hardcoded
  `1735084800000L`** (Dec 2024). Now that `currentTimeMillis()` is in
  `:core:model`, use it. **S.**
- **wasmJs `SettingsStorage` persists nothing** — Phase 6 names the class;
  giving it `localStorage` like js is a separate small change. **S.**
- **Per-feature `AppStrings` split** — only if measured incremental build times
  justify it. **S–M.**
- **Effects carrying semantic values instead of localized prose** — the real fix
  for ViewModels producing copy. Its own project. **M.**

---

## The iOS framework constraint

`iosApp/iosApp.xcodeproj/project.pbxproj:155` hardcodes
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`. That is the **only**
occurrence of `composeApp` in the pbxproj, and the file has no
`FRAMEWORK_SEARCH_PATHS` or `ComposeApp` literal — so a rename is technically a
one-line edit.

**Keep the module path `:composeApp` anyway.**

1. The rename buys nothing architecturally. What matters is that the module's
   *role* shrinks from "the app" to "the composition root". The path is a label.
2. The pbxproj is outside Gradle's verification loop. A typo fails only at Xcode
   build time, which `./gradlew check` never reaches, and stale DerivedData can
   mask a bad edit.
3. `composeApp` appears in nine places in `README.md` and in muscle memory
   (`:composeApp:assembleStaging`, `verifyReleaseSigning`).
4. The framework `baseName` must stay `"ComposeApp"` because Swift does
   `import ComposeApp`. Renaming the module to `:app` leaves the framework still
   called `ComposeApp` — the inconsistency moves rather than disappearing.

`:composeApp` stays the **only** framework producer; every `:core:*` and
`:feature:*` is a plain KMP library with no `binaries.framework` block,
statically linked into `ComposeApp.framework` by the toolchain.

**One detail to know:** `isStatic = true` links dependency klibs in
automatically, but their public API is **not** exported to the Obj-C header
unless declared:

```kotlin
binaries.framework {
    baseName = "ComposeApp"; isStatic = true
    // only if Swift ever needs a type from a core module:
    export(projects.core.model)
}
```

`iosApp` touches only `MainViewControllerKt` today, so no `export` is needed. If
that changes, `export(...)` requires the dependency to be `api(...)` not
`implementation(...)`, and a mismatch produces a confusing link error.

---

## Phase summary

| # | Goal | Effort | Risk |
|---|---|---|---|
| 0 | build-logic, convention plugins, catalog, CC-safe BuildConfig | M | Low |
| 1 | Shared services; delete VM-in-VM (in place) | L | **High** (semantic) |
| 2 | Decompose `NewsViewModel` + `NewsUiState` (in place) | **XL** | **Highest** (semantic) |
| 3 | Break cycles A/B/C + data→presentation (package moves) | M | Low |
| 4 | `:core:contract` + `:core:testing` + server dedup + Docker | M | **Med-high** (deploy, share slugs) |
| 5 | `:core:{config,model,domain}` | L | Low |
| 6 | `:core:data` + `SettingsStorage` interface + no-op collapse | L | Medium |
| 7 | `:core:localization` + `:core:ui` (fonts, drawables) | M | **Medium** (iOS/wasm packaging) |
| 8 | `:core:navigation` + `Navigator` | M | Medium (cold-start deep links) |
| 9 | Six `:feature:*` modules | M | Low |
| 10 | DI restructure + `viewModel { }` switch | M | Medium |
| 11 | Optional cleanups | S | Low |

---

## Execution protocol

**Division of labour:** Codex writes the code; Claude decides what gets built,
reviews it, verifies it on the running emulator, and lands it.

### Step 0 — one-time setup

Copy this plan into the repo as `docs/modularization-plan.md` and commit it.
Codex then reads it by a short repo-relative path instead of receiving it pasted
into every prompt. `MODULARIZATION.md` (the checklist) sits next to it at the
repo root.

### Per-phase loop

1. **Claude** creates the branch: `git checkout -b modularization-phase-N`.
2. **Claude** opens **one Codex thread for the phase** with the briefing below,
   at the effort level from the table. Subsequent commits inside the same phase
   continue that thread with `codex-reply` — never a fresh `codex` call, which
   would re-read the plan and re-derive the codebase map.
3. **Codex** writes the code and reports what it changed.
4. **Claude** reviews: reads the diff, runs the phase's verification commands,
   and drives the Android emulator already running in Android Studio for the
   phases whose verification calls for it (2, 6, 7, 8, 9, 10).
5. If something is wrong, **Claude** sends the specific defect back via
   `codex-reply` — not a new thread.
6. When green, **Claude** commits, pushes, opens the PR, merges to `master`,
   pulls, ticks `MODULARIZATION.md`, and starts the next phase from a fresh
   branch off the updated `master`.

### Codex settings

- Model: `gpt-5.5`
- Sandbox: `workspace-write` (it is writing code)
- Approval policy: `never`
- Effort: per the table below

| Phase | Effort | Why |
|---|---|---|
| 0 build-logic | `xhigh` | build design, configuration cache, Docker target gating |
| 1 shared services | `xhigh` | highest semantic risk after phase 2 |
| 2 VM decomposition | `xhigh` | highest risk in the plan |
| 3 cycle breaking | `medium` | package moves; the compiler catches every mistake |
| 4 contract + testing | `high` | Docker deploy and share-slug stability |
| 5 config/model/domain | `medium` | ~40 file moves |
| 6 core:data | `high` | one real refactor (`SettingsStorage`) touching persisted keys |
| 7 localization + ui | `medium` | moves + resource packaging |
| 8 navigation | `high` | small design + cold-start ordering |
| 9 six features | `low` | pure moves |
| 10 DI + `viewModel { }` | `high` | behavioural change |

### The briefing template

Every phase-opening prompt has the same five parts and stays short — the detail
lives in the repo, not in the prompt:

```
Phase N of the modularization in docs/modularization-plan.md. Read that file's
"Phase N" section and its "Review corrections" table before doing anything.

SCOPE — do exactly this, nothing more:
  <the phase's goal in one or two lines>

FILES — these are the ones that change (already mapped; do not go exploring):
  <exact paths, with file:line where a specific site matters>

CONSTRAINTS:
  - Do not touch anything outside the listed files.
  - Do not re-propose anything in the "Review corrections" table.
  - <the phase's specific traps, e.g. "the cache format does not change">

DONE WHEN:
  <the phase's verification commands, verbatim>

Commit at every point the build is green, using the convention in CLAUDE.md.
Report which files you changed and which verification commands you ran.
```

The `FILES` block is what keeps consumption down: the codebase map already
exists in the plan, so Codex is told where to work instead of searching for it.

### PR format

Plain and readable by anyone, no template ceremony:

```markdown
## The problem
<two or three sentences: what was wrong before this change, in plain language>

## What changed
- <one bullet per idea, saying what changed and what it means>
- <not a file list — the diff is the file list>

## How it was verified
- <the commands that ran, and what was checked on the emulator>
```

Title: the commit subject. No `feat:`/`fix:` prefixes, per CLAUDE.md.

---

## Resumability

The work spans many sessions and may hit usage limits mid-phase. The protocol:

1. **Create `MODULARIZATION.md` at the repo root before starting**, containing
   the phase table above as a checklist, with a one-line "state" note per phase.
   Commit it in Phase 0.
2. **One phase = one PR.** Within a phase, commit at every point the build is
   green (Phase 2 explicitly plans seven such points). Commit subjects name the
   phase: `Add build-logic convention plugins (modularization phase 0)`.
3. **Tick a phase in `MODULARIZATION.md` only when its verification passes**,
   and commit that tick as the last commit of the PR.
4. **On resuming**, a session runs `git status` and `git log --oneline -20`, then
   reads `MODULARIZATION.md`. Uncommitted work = a phase in progress; the first
   unticked phase = what to do next.
5. **Never start phase *n+1* with phase *n* unverified.** The order exists so
   that risk decreases: 0 and 3–5 are mechanical, 1–2 are semantic rewrites,
   6–10 are module extraction.
6. **Update the phase-roadmap memory** when the whole thing lands, noting that
   modularization completed and where `MODULARIZATION.md` lives.

---

## Files touched most

- `settings.gradle.kts` — 2 `include`s → ~17, plus `includeBuild("build-logic")`
- `composeApp/build.gradle.kts` (398) — shrinks to roughly 120: the Android
  application block, `compose.desktop`, signing verification, project deps
- `server/deploy/settings.gradle.kts` + `server/Dockerfile` — the pair that
  constrains `:core:contract`'s target set; both must change in Phase 4 or the
  deploy silently breaks
- `presentation/viewmodel/NewsViewModel.kt` (1,476) — deleted by Phase 2
- `presentation/mvi/NewsUiState.kt` (323) + `NewsUiEvent.kt` (138) — deleted by
  Phase 2, contents redistributed
- `presentation/ui/screen/NewsScreen.kt` (603) — splits three ways; the cross-VM
  search adapter at `:135-156` disappears
- `presentation/ui/components/ProfileScreen.kt` (434) — moves to
  `:feature:settings` and stops being filed as a component
- `di/PresentationModule.kt` — split across six feature modules + the shell
- `presentation/localization/AppStrings.kt` (711) — **moves intact**, not split

---

## Review corrections

Both reviewers checked the first draft against the code. What they overturned,
so nobody re-proposes it:

| Claim in the first draft | Correction | Source |
|---|---|---|
| Split Gradle modules first, refactor after | **Reversed.** Semantics first (1–3), modules second (4–10) | both |
| The cache stores wire DTOs; needs a schema bump and a purge | **False.** `NewsLocalDataSource.kt:28` already has its own `CachedArticle`; `:117` maps into it. **No migration, no purge.** | Codex |
| `AccountSyncCoordinator` becomes an observing `single` | **Rejected** — StateFlows emit immediately, defeating the load gate at `AccountSyncCoordinator.kt:62` and breaking three tests. Use a call-driven `AccountSyncUseCase` returning a value | Codex, Plan |
| Split `AppStrings` into per-feature slices | **Deferred.** A shared core interface creates no illegal edge; splitting costs 8 contracts + 16 objects for a rebuild-surface win | both |
| `ArticleDeepLinks` moves to a Ktor-free `:core:contract` | **Needs splitting, not moving** — imports Ktor `Url` (`:3`,`:54`) and `NewsArticle` (`:151`,`:163`) | Codex |
| `publicResClass = true`; failures show as missing fonts | **False** — cross-module `internal Res` access is a compile error. Keep `false`, expose `appLogoPainter()` | Codex, Plan |
| `:core:testing` in Phase 11 | **Moved to Phase 4** — Phase 5–6 tests already import the fakes | Codex |
| A commonTest `actual` for `SettingsStorage` | **Rejected** — collides with per-target actuals. Make it an interface | both |
| 3 separate modules for network/database/data; `:core:ui` + `:core:designsystem` separate | **Merged** to `:core:data` and `:core:ui` | Codex |
| `ProfileScreen` → `:feature:auth` | **`:feature:settings`** — it imports `SavedArticlesUiState` (`:49`,`:66`) | Codex |
| `:feature:onboarding` as its own module | **Stays in `:composeApp`** — it imports `SettingsUiState` (`:35`,`:58`) and is a shell route | both |
| The buses are eager singles | **They are lazy** (`DataModule.kt:30-32`); the comment at `:29` is wrong. Make them `createdAtStart = true` | Codex |
| `NewsUiState` has 29 fields | **32** (`:15`–`:127`) | Codex |
| A single generic `koinVM<T>()` replaces the 4 expect/actual sets | **Rejected** — android/jvm and ios/js/wasm have different ownership semantics; keep expect/actual | Codex |
| `AppPreferences` moves to model alone | **`NotificationPreferenceKeys` must move with it** (`AppPreferences.kt:32`) | Codex |
| `AndroidDeviceIntegrityInspector` moves as-is | **Blocked** — imports the AGP `BuildConfig` (`:9`). Inject the signing digest and debug flag | Codex |
| `cd server/deploy && ./gradlew …` as verification | **Invalid** — the wrapper is at repo root. Use `docker build -f server/Dockerfile .`, and note the Dockerfile copies only `server/`, so `:core:contract` needs explicit `COPY` lines plus target gating | Codex, Plan |
