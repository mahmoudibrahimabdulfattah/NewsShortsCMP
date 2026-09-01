# wasmJs verification

Verified on 2026-09-01 against the `stage-de-full` branch.

## Target status

The application declares both `js` and `wasmJs` browser executable targets.
The earlier assumption that only `wasmJs` remained is therefore stale.

The wasm browser test build is healthy:

```text
./gradlew :composeApp:wasmJsBrowserTest --console=plain --rerun-tasks
BUILD SUCCESSFUL in 44s
```

Its JUnit XML contains 218 tests with zero failures, errors, or skips.

The production distribution does not build with the repository's configured
3 GiB Kotlin daemon heap:

```text
./gradlew :composeApp:wasmJsBrowserDistribution \
  :composeApp:wasmJsBrowserTest --console=plain --rerun-tasks
Execution failed for task ':composeApp:compileProductionExecutableKotlinWasmJs'.
java.lang.OutOfMemoryError: GC overhead limit exceeded
BUILD FAILED in 1m 37s
```

This distinguishes the supported test/development compiler path from the
whole-program production compiler path. A production wasm artifact is not
currently reproducible with the checked-in build settings.

## Shipping status

No repository workflow builds or deploys either web application target. The
only publishing workflow runs `:server:generateStaticFeed` into `build/site`,
then uploads that directory to Cloudflare and GitHub Pages. Its triggers cover
the server, Cloudflare configuration, and the feed workflow itself; they do not
cover `composeApp`.

The README exposes `wasmJsBrowserRun` only as a local development command. No
deployment configuration consumes `wasmJsBrowserDistribution` or any path
under `composeApp/build/dist/wasmJs`.

## Decision

The `wasmJs` target remains in place. It is tested and useful for local
development, but it is not shipped, and production packaging needs a separate
memory/build investigation before that changes.
