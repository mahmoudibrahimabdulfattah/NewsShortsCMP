# News Shorts 📰

A beautiful **Compose Multiplatform** news reader app with a TikTok-style vertical scrolling experience. Built with Clean Architecture, MVI pattern, and works across **Android**, **iOS**, **Desktop**, and **Web**.

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-purple.svg)
![Compose](https://img.shields.io/badge/Compose-1.9.3-blue.svg)
![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20Desktop%20%7C%20Web-green.svg)
![Architecture](https://img.shields.io/badge/Architecture-Clean%20%2B%20MVI-orange.svg)

---

## ✨ Features

### 📱 Core Experience
- **Vertical Pager** — Swipe up/down to browse news like TikTok/Shorts
- **Beautiful UI** — Midnight Ocean dark theme with vibrant accents
- **Categories** — General, Technology, Business, Entertainment, Sports, Science, Health
- **Multi-language News** — Support for 12 languages (EN, AR, DE, ES, FR, IT, NL, NO, PT, RU, ZH, HE)
- **Country Selection** — News from 13 countries worldwide

### 🚀 Performance Optimizations
- **Fast Startup** — Optimized splash screen (~450ms vs ~1200ms)
- **Parallel Animations** — Smooth 400ms splash animation
- **Lazy Loading** — Dependencies loaded on-demand
- **Crossfade Transitions** — Seamless screen transitions

### 📦 Offline-First Architecture
- **Persistent Cache** — News stored locally for instant access
- **Background Refresh** — Cached data shown instantly, fresh data fetched in background
- **Smart Storage** — LRU eviction with fixed ~500KB limit
- **24-Hour Validity** — Auto-refresh stale cache

### 🔄 User Experience
- **Pull to Refresh** — Swipe down at first article to refresh
- **Save Articles** — Bookmark articles for later reading
- **Share Articles** — Share news with friends
- **RTL Support** — Full Arabic/Hebrew layout support
- **Bilingual UI** — English & Arabic interface

### 🌐 Cross-Platform
- **Single Codebase** — One code for all platforms
- **Unified Splash Screen** — Consistent branding across platforms
- **Native Feel** — Platform-specific optimizations

---

## 📸 Screenshots

| For You | Countries | Profile |
|---------|-----------|---------|
| Vertical news feed | News by country | Settings & saved |

---

## 🏛️ Architecture

```
composeApp/src/commonMain/kotlin/org/example/newsshorts/
├── domain/
│   ├── model/          # NewsArticle, NewsCategory, NewsResult
│   ├── repository/     # NewsRepository interface
│   └── use_case/       # GetTopHeadlinesUseCase (with caching)
├── data/
│   ├── remote/         # NewsApiClient, Ktor HTTP client
│   ├── local/          # NewsLocalDataSource (persistent cache)
│   ├── mapper/         # DTO to Domain mappers
│   └── repository/     # Offline-first implementation
├── presentation/
│   ├── mvi/            # UiState, UiEvent, UiEffect
│   ├── viewmodel/      # Platform-agnostic ViewModels
│   ├── localization/   # Multi-language support
│   └── ui/
│       ├── theme/      # NewsShortsTheme, Typography
│       ├── components/ # Reusable UI components
│       └── screen/     # NewsScreen, SplashScreen
├── di/                 # Koin dependency injection
└── App.kt              # Main entry point
```

---

## 🛠️ Tech Stack

| Component | Library | Version |
|-----------|---------|---------|
| **Language** | Kotlin Multiplatform | 2.3.0 |
| **UI Framework** | Compose Multiplatform | 1.9.3 |
| **DI** | Koin Multiplatform | 4.0.4 |
| **Networking** | Ktor Client | 3.1.3 |
| **Serialization** | Kotlinx Serialization | 1.8.0 |
| **Image Loading** | Coil 3 | 3.2.0 |
| **Coroutines** | Kotlinx Coroutines | 1.10.2 |
| **Architecture** | Clean Architecture + MVI | — |

---

## 🚀 Running the App

### Prerequisites

- JDK 17 or later
- Android Studio Ladybug / IntelliJ IDEA
- Xcode 15+ (for iOS)
- News API Key from [newsapi.org](https://newsapi.org/)

### Setup API Key

1. Get a free API key from [NewsAPI.org](https://newsapi.org/register)
2. Create/edit `local.properties` in the project root:
```properties
NEWS_API_KEY=your_api_key_here
```

### Android

```bash
./gradlew :composeApp:installDebug
# Or run directly from Android Studio
```

### iOS

```bash
# Build the shared framework first
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

# Then open in Xcode
open iosApp/iosApp.xcodeproj
```

### Desktop (JVM)

```bash
./gradlew :composeApp:run
```

### Web (JavaScript)

```bash
./gradlew :composeApp:jsBrowserRun
```

### Web (WASM)

```bash
./gradlew :composeApp:wasmJsBrowserRun
```

---

## 📊 Performance Metrics

| Metric | Value |
|--------|-------|
| **Splash Duration** | ~450ms |
| **Cache Size Limit** | ~500KB |
| **Articles per Category** | 20 max |
| **Cache Entries** | 10 max |
| **Cache Expiry** | 24 hours |
| **Startup Improvement** | 62% faster |

---

## 📁 Project Structure

```
News Shorts CMP/
├── composeApp/
│   └── src/
│       ├── commonMain/     # Shared business logic & UI
│       ├── androidMain/    # Android platform code
│       ├── iosMain/        # iOS platform code
│       ├── jvmMain/        # Desktop platform code
│       ├── jsMain/         # JS web platform code
│       └── wasmJsMain/     # WASM web platform code
├── iosApp/                 # iOS app entry point
├── gradle/
│   └── libs.versions.toml  # Version catalog
└── build.gradle.kts
```

---

## 🎯 Key Implementation Details

### Offline-First Flow
```
App Opens → Check Cache → Show Cached (instant) → Fetch Fresh (background) → Update UI
```

### Splash Screen Optimization
```
Before: Animation(600ms) → Delay(300ms) → Text(550ms) → Exit(300ms) = ~1750ms
After:  Parallel Animation(400ms) → Crossfade(150ms) = ~550ms
```

### Cache Strategy
- **LRU Eviction**: Oldest accessed entries removed first
- **Fixed Size**: Never exceeds 200 articles (~500KB)
- **Persistence**: Survives app restarts
- **Smart Refresh**: Background update without blocking UI

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

<p align="center">
  Built with ❤️ using <b>Kotlin Multiplatform</b> & <b>Compose Multiplatform</b>
</p>
