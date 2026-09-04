import com.mk.newsshorts.buildlogic.LocalPropertiesValueSource
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.androidApplication)
    id("newsshorts.kmp.app")
    alias(libs.plugins.composeHotReload)
}

// google-services.json is not in version control, so the Firebase plugins are
// applied only when it is present. A clone without it still builds; reporting
// just stays off (see AnalyticsReporter).
val hasFirebaseConfig: Boolean = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = libs.plugins.googleServices.get().pluginId)
    apply(plugin = libs.plugins.firebaseCrashlytics.get().pluginId)
}

val localProperties: Map<String, String> = providers.of(LocalPropertiesValueSource::class) {
    parameters.propertiesFile.set(rootProject.layout.projectDirectory.file("local.properties"))
}.get()

// Shared links point at the published site, never at a local server: a link
// sent to someone else has to resolve on their device.
val shareBaseUrl: String = localProperties["SHARE_BASE_URL"]
    ?: "https://mahmoudibrahimabdulfattah.github.io/NewsShortsCMP"

// The App Links filter has to match the links the app actually produces, so
// both are derived from the one value rather than repeated in the manifest.
val shareLinkHost: String = shareBaseUrl
    .substringAfter("://")
    .substringBefore('/')
    .substringBefore(':')
val shareLinkPathPrefix: String = shareBaseUrl
    .substringAfter("://")
    .substringAfter('/', missingDelimiterValue = "")
    .trimEnd('/')
    .let { path -> if (path.isEmpty()) "/a/" else "/$path/a/" }

// SHA-256 of the certificate the released app is signed with — the Play App
// Signing one, since Play re-signs every build it serves. Empty until there is
// a release keystore, which disables the tamper check rather than failing it.
val expectedSigningSha256: String = localProperties["SIGNING_CERT_SHA256"].orEmpty()

// The ValueSource read makes local.properties a tracked configuration input.
// These values are reduced to plain strings during configuration, so the
// verification task serializes only the already-computed problem list.
val releaseSigningProblems: List<String> = run {
    val problems = ArrayList<String>()
    listOf(
        "RELEASE_STORE_FILE",
        "RELEASE_STORE_PASSWORD",
        "RELEASE_KEY_ALIAS",
        "RELEASE_KEY_PASSWORD",
    ).forEach { name ->
        if (localProperties[name].isNullOrBlank()) {
            problems.add("$name is missing or blank")
        }
    }
    val storePath = localProperties["RELEASE_STORE_FILE"]
    if (!storePath.isNullOrBlank() && !file(storePath).exists()) {
        problems.add("RELEASE_STORE_FILE points at $storePath, which does not exist")
    }
    problems
}

// Passwordless sign-in links land on the Firebase project's own Auth domain,
// which serves the App Links assetlinks file itself — so the app can claim the
// link without us hosting anything. The manifest filter needs that host as a
// literal and cannot read it at runtime, so it is parsed out of
// google-services.json rather than written down twice. Without that file there
// is no Firebase at all (see createAuthClient), so an unmatchable host leaves
// the filter inert instead of breaking the merge.
val firebaseAuthLinkHost: String = file("google-services.json")
    .takeIf { it.exists() }
    ?.readText()
    ?.let { Regex("\"project_id\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
    ?.let { projectId -> "$projectId.firebaseapp.com" }
    ?: "invalid.invalid"

// gradle.properties is the one place the app version is declared. The Android
// block and generated BuildConfig both read those properties, so the number the
// update check compares against is by construction the number Play installed.
val appVersionCode = providers.gradleProperty("newsshorts.versionCode").map(String::toInt)
val appVersionName = providers.gradleProperty("newsshorts.versionName")

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            api(projects.core.config)
            api(projects.feature.auth)
            api(projects.feature.feed)
            api(projects.feature.inbox)
            api(projects.feature.saved)
            api(projects.feature.search)
            api(projects.feature.settings)
            api(projects.core.contract)
            api(projects.core.data)
            api(projects.core.model)
            api(projects.core.navigation)
            api(projects.core.localization)
            api(projects.core.ui)
            api(projects.core.domain)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            // Lifecycle for Android
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.composeViewModel)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
            implementation(libs.androidx.browser)
            implementation(libs.androidx.glance.appwidget)
        }
        iosMain.dependencies {
            // Note: koin-compose-viewmodel has compatibility issues on iOS Native
            // Using direct Koin injection instead
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            // Lifecycle for Desktop
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.composeViewModel)
        }
    }
}

android {
    namespace = "com.mk.newsshorts"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // The identity Play and Firebase use, and the one baked into every
        // share link. It can never be changed once the app is published.
        applicationId = "com.mk.newsshorts"
        manifestPlaceholders["shareLinkHost"] = shareLinkHost
        manifestPlaceholders["shareLinkPathPrefix"] = shareLinkPathPrefix
        manifestPlaceholders["firebaseAuthLinkHost"] = firebaseAuthLinkHost
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode.get()
        versionName = appVersionName.get()
        buildConfigField("String", "EXPECTED_SIGNING_SHA256", "\"$expectedSigningSha256\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        // Only for DEBUG: the release build must be able to tell that it is a
        // release without asking the OS, which an attacker controls.
        buildConfig = true
    }
    // A release build has to be signed before it can be uploaded. The keystore
    // comes from local.properties, which is not in version control, and the
    // release variant must fail closed when any part of that secret is absent.
    val releaseStorePath: String? = localProperties["RELEASE_STORE_FILE"]
    val releaseKeystore: File? = releaseStorePath
        ?.takeUnless { it.isBlank() }
        ?.let(::file)
        ?.takeIf { it.exists() }
    if (releaseSigningProblems.isEmpty() && releaseKeystore != null) {
        signingConfigs.create("release") {
            storeFile = releaseKeystore
            storePassword = localProperties["RELEASE_STORE_PASSWORD"]
            keyAlias = localProperties["RELEASE_KEY_ALIAS"]
            keyPassword = localProperties["RELEASE_KEY_PASSWORD"]
        }
    }

    buildTypes {
        getByName("release") {
            // No debug-key fallback: the debug keystore ships with the Android
            // SDK and is public, so a release artifact signed with it binds the
            // app's Play identity to a key anyone possesses. Unconfigured now
            // means unsigned, and the verifyReleaseSigning guard below stops
            // the build before it gets that far. Local R8 testing has its own
            // home: the staging build type.
            signingConfig = signingConfigs.findByName("release")
            // R8 both shrinks and renames. The renaming is not the security
            // control — anyone determined will still read the app — but it
            // raises the cost of a casual repackage, and the shrinking is what
            // keeps a Compose + Ktor + Firebase app down to a sane size.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            versionNameSuffix = "-staging"
            // Staging is deliberately debug-signed, so the certificate check
            // has no true answer to give: comparing a debug key against the
            // Play signer would fail every staging build for the one property
            // that makes it installable. Empty is what the inspector already
            // reads as "check disabled". Release keeps the real SHA-256; this
            // narrows staging, not the check.
            buildConfigField("String", "EXPECTED_SIGNING_SHA256", "\"\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Fails the release variant when local.properties has no usable keystore."
    val problems = releaseSigningProblems.toTypedArray()
    doFirst {
        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Release signing is not configured. Fix these in local.properties before building the release variant:")
                    problems.forEach { appendLine("  - $it") }
                    appendLine("Refusing to sign with the debug key: it ships with the Android SDK and is public.")
                    append("For local R8 testing without production credentials, run :composeApp:assembleStaging instead.")
                },
            )
        }
    }
}
tasks.matching {
    it.name in setOf("assembleRelease", "bundleRelease", "packageRelease", "packageReleaseBundle")
}.configureEach {
    dependsOn(verifyReleaseSigning)
}

compose.resources {
    generateResClass = never
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.mk.newsshorts.MainKt"
        
        buildTypes.release.proguard {
            isEnabled = false
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "NewsShorts"
            packageVersion = appVersionName.get()
            includeAllModules = true
            
            macOS {
                iconFile.set(project.file("src/jvmMain/resources/icons/icon-512.png"))
                bundleID = "com.mk.newsshorts"
            }
            
            windows {
                iconFile.set(project.file("src/jvmMain/resources/icons/icon-256.png"))
            }
            
            linux {
                iconFile.set(project.file("src/jvmMain/resources/icons/icon-512.png"))
            }
        }
    }
}
