import com.mk.newsshorts.buildlogic.GenerateBuildConfig
import com.mk.newsshorts.buildlogic.LocalPropertiesValueSource
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

val localProperties = providers.of(LocalPropertiesValueSource::class) {
    parameters.propertiesFile.set(rootProject.layout.projectDirectory.file("local.properties"))
}
val localPropertyValues: Map<String, String> = localProperties.get()

val generateBuildConfig = tasks.register<GenerateBuildConfig>("generateBuildConfig") {
    packageName.set("com.mk.newsshorts.config")
    backendOrigins.set(
        localPropertyValues["BACKEND_ORIGINS"]
            ?: localPropertyValues["BACKEND_BASE_URL"]
            ?: "http://localhost:8091",
    )
    shareBaseUrl.set(
        localPropertyValues["SHARE_BASE_URL"]
            ?: "https://mahmoudibrahimabdulfattah.github.io/NewsShortsCMP",
    )
    versionCode.set(providers.gradleProperty("newsshorts.versionCode").map(String::toInt))
    versionName.set(providers.gradleProperty("newsshorts.versionName"))
    // The Firebase project's "Web client (auto created by Google Service)" OAuth
    // client ID — Credential Manager needs it as the audience for the Google ID
    // token even though this is an Android app, because Firebase Auth verifies
    // that token against a web client. Empty until it exists, which disables the
    // Google Sign-In button rather than crashing (see AuthClient).
    googleWebClientId.set(localPropertyValues["GOOGLE_WEB_CLIENT_ID"].orEmpty())
    // Required by Google Play once an app has accounts, and already advertised to
    // readers by Google's own sign-in consent sheet. Overridable so a fork does
    // not ship a link to someone else's policy.
    privacyPolicyUrl.set(
        localPropertyValues["PRIVACY_POLICY_URL"]
            ?: "https://mahmoudibrahimabdulfattah.github.io/newsshorts-privacy/",
    )
    outputDir.set(layout.buildDirectory.dir("generated/buildconfig/commonMain/kotlin"))
}

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    extensions.configure<KotlinMultiplatformExtension>("kotlin") {
        sourceSets.named("commonMain") {
            kotlin.srcDir(generateBuildConfig)
        }
    }
}
