# News Shorts 📰

A beautiful **Compose Multiplatform** news reader app with a TikTok-style vertical scrolling experience. Built with Clean Architecture, MVI pattern, and works across **Android**, **iOS**, **Desktop**, and **Web**.

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-purple.svg)
![Compose](https://img.shields.io/badge/Compose-1.9.3-blue.svg)
![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20Desktop%20%7C%20Web-green.svg)

## ✨ Features

- 📱 **Vertical Pager**: Swipe up/down to browse news like TikTok/Shorts
- 🎨 **Beautiful UI**: Midnight Ocean dark theme with vibrant accents
- 📂 **Categories**: Browse news by General, Technology, Business, Entertainment, Sports, Science, and Health
- 🔄 **Real-time Updates**: Pull to refresh for latest news
- 🌐 **Cross-Platform**: Single codebase for Android, iOS, Desktop, and Web
- 🏗️ **Clean Architecture**: Domain, Data, and Presentation layers
- 🔌 **MVI Pattern**: Unidirectional data flow with UiState, UiEvent, UiEffect

## 🏛️ Architecture

```
composeApp/src/commonMain/kotlin/org/example/newsshorts/
├── domain/
│   ├── model/          # NewsArticle, NewsCategory, NewsResult
│   ├── repository/     # NewsRepository interface
│   └── use_case/       # GetTopHeadlinesUseCase, SearchNewsUseCase
├── data/
│   ├── remote/         # NewsApiClient, DTOs
│   ├── local/          # MockNewsDataSource
│   ├── mapper/         # NewsMapper
│   └── repository/     # NewsRepositoryImpl
├── presentation/
│   ├── mvi/            # NewsUiState, NewsUiEvent, NewsUiEffect
│   ├── viewmodel/      # NewsViewModel
│   └── ui/
│       ├── theme/      # NewsShortsTheme, Typography
│       ├── components/ # NewsCard, CategoryChip, etc.
│       └── screen/     # NewsScreen
├── di/                 # Koin modules
└── App.kt              # Main composable
```

## 🛠️ Tech Stack

| Component | Library |
|-----------|---------|
| UI Framework | Compose Multiplatform |
| DI | Koin Multiplatform |
| Networking | Ktor Client |
| JSON | Kotlinx Serialization |
| Image Loading | Coil 3 |
| Date/Time | Kotlinx DateTime |
| Architecture | Clean Architecture + MVI |

## 🚀 Running the App

### Prerequisites

- JDK 17 or later
- Android Studio / IntelliJ IDEA
- Xcode (for iOS)

### Android

```bash
./gradlew :composeApp:assembleDebug
# Or run directly from Android Studio
```

### iOS

1. Open `iosApp/iosApp.xcodeproj` in Xcode
2. Select a simulator or device
3. Click Run

### Desktop (JVM)

```bash
./gradlew :composeApp:run
```

### Web (JS)

```bash
./gradlew :composeApp:jsBrowserRun
```

### Web (WASM)

```bash
./gradlew :composeApp:wasmJsBrowserRun
```

## 🔑 API Configuration

The app uses [NewsAPI.org](https://newsapi.org/) for fetching real news. To use real data:

1. Get a free API key from [NewsAPI.org](https://newsapi.org/register)
2. Open `composeApp/src/commonMain/kotlin/org/example/newsshorts/data/remote/NewsApiClient.kt`
3. Replace `YOUR_NEWS_API_KEY` with your actual API key

**Note**: The app includes mock data that works without an API key for demo purposes.

## 📁 Project Structure

```
StartComposeMultiplatform/
├── composeApp/
│   └── src/
│       ├── commonMain/     # Shared code
│       ├── androidMain/    # Android-specific
│       ├── iosMain/        # iOS-specific
│       ├── jvmMain/        # Desktop-specific
│       ├── jsMain/         # JS web-specific
│       ├── wasmJsMain/     # WASM web-specific
│       └── webMain/        # Shared web resources
├── iosApp/                 # iOS app wrapper
├── gradle/                 # Gradle configuration
└── build.gradle.kts        # Root build file
```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---

Built with ❤️ using Kotlin Multiplatform and Compose Multiplatform
