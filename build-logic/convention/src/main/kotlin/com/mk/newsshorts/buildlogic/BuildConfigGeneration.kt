package com.mk.newsshorts.buildlogic

import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty

abstract class LocalPropertiesValueSource : ValueSource<Map<String, String>, LocalPropertiesValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        @get:InputFile
        @get:Optional
        @get:PathSensitive(PathSensitivity.RELATIVE)
        val propertiesFile: RegularFileProperty
    }

    override fun obtain(): Map<String, String> {
        val file = parameters.propertiesFile.asFile.orNull ?: return emptyMap()
        if (!file.isFile) return emptyMap()

        return Properties()
            .apply { file.inputStream().use { input -> load(input) } }
            .entries
            .associate { (key, value) -> key.toString() to value.toString() }
    }
}

abstract class GenerateBuildConfig : DefaultTask() {
    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val backendOrigins: Property<String>

    @get:Input
    abstract val shareBaseUrl: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val googleWebClientId: Property<String>

    @get:Input
    abstract val privacyPolicyUrl: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputDirectory = outputDir.get().asFile
        outputDirectory.deleteRecursively()

        val packageValue = packageName.get()
        val outputFile = outputDirectory
            .resolve(packageValue.replace('.', '/'))
            .resolve("BuildConfig.kt")

        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            |package $packageValue
            |
            |object BuildConfig {
            |    const val BACKEND_ORIGINS: String = ${backendOrigins.get().asKotlinStringLiteral()}
            |    const val SHARE_BASE_URL: String = ${shareBaseUrl.get().asKotlinStringLiteral()}
            |    const val VERSION_CODE: Int = ${versionCode.get()}
            |    const val VERSION_NAME: String = ${versionName.get().asKotlinStringLiteral()}
            |    const val GOOGLE_WEB_CLIENT_ID: String = ${googleWebClientId.get().asKotlinStringLiteral()}
            |    const val PRIVACY_POLICY_URL: String = ${privacyPolicyUrl.get().asKotlinStringLiteral()}
            |}
            |""".trimMargin(),
        )
    }

    private fun String.asKotlinStringLiteral(): String =
        buildString {
            append('"')
            this@asKotlinStringLiteral.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
}
