plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "com.mk.newsshorts"
version = "0.1.0"

application {
    mainClass.set("com.mk.newsshorts.server.ApplicationKt")
}

/** Runs one ingestion cycle and writes the feed as static JSON for GitHub Pages. */
tasks.register<JavaExec>("generateStaticFeed") {
    group = "application"
    description = "Fetches, summarizes, and writes the news feed as static JSON files."
    mainClass.set("com.mk.newsshorts.server.ApplicationKt")
    classpath = sourceSets["main"].runtimeClasspath
    // Run from the repository root so -PoutputDir and DB_PATH are relative to
    // it rather than to server/, matching what CI passes in.
    workingDir = rootProject.projectDir
    args("--generate-static", project.findProperty("outputDir")?.toString() ?: "build/site")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(projects.core.contract)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.ktor.server.callLogging)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutinesCore)

    implementation(libs.rome)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.javaTime)
    implementation(libs.sqlite.jdbc)
    implementation(libs.logback.classic)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.kotlin.test)
}
