import java.util.Properties
import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.tasks.Internal
plugins {
    id("com.android.application")
}

val signingPropertiesFile = rootProject.file("signing.properties")
val signingProperties = Properties()
if (signingPropertiesFile.exists()) {
    signingPropertiesFile.inputStream().use { signingProperties.load(it) }
}
val hasReleaseSigning = signingPropertiesFile.exists()

android {
    namespace = "com.lycaennaa.twitchalerts"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lycaennaa.twitchalerts"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.2"
    }
    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
                storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                storeType = "PKCS12"
            }
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
dependencies {
    implementation("androidx.work:work-runtime:2.10.0")
    testImplementation("junit:junit:4.13.2")
}

val apkAppName = "Twitch Live Alerts"

androidComponents {
    onVariants { variant ->
        val outputName =
            "$apkAppName - ${variant.outputs.single().versionName.get()} - ${variant.buildType?.uppercase() ?: variant.name.uppercase()}.apk"
        val variantSuffix = variant.name.replaceFirstChar { it.uppercase() }
        val apkArtifact = variant.artifacts.get(SingleArtifact.APK)
        val apkFiles = apkArtifact.map { apkDirectory ->
            project.fileTree(apkDirectory.asFile) {
                include("*.apk")
                exclude(outputName)
            }
        }
        val namedApk = layout.buildDirectory.file("outputs/apk/${variant.name}/$outputName")
        val finalizedApkTask = tasks.register<FinalizeApkTask>("finalize${variantSuffix}Apk") {
            inputApks.from(apkFiles)
            outputApk.set(namedApk)
            metadataFile.set(apkArtifact.map { it.file("output-metadata.json") })
            outputs.upToDateWhen { false }
        }

        tasks.configureEach {
            if (name == "assemble$variantSuffix") {
                finalizedBy(finalizedApkTask)
            }
        }
    }
}

abstract class FinalizeApkTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputApks: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputApk: RegularFileProperty

    @get:Internal
    abstract val metadataFile: RegularFileProperty

    @TaskAction
    fun finalizeApk() {
        val source = inputApks.singleFile
        val output = outputApk.get().asFile
        val metadata = metadataFile.get().asFile
        output.parentFile.mkdirs()

        val document = JsonSlurper().parse(metadata) as MutableMap<String, Any?>
        val elements = document["elements"] as List<MutableMap<String, Any?>>
        check(elements.size == 1) { "Expected one APK output, found ${elements.size}" }
        elements.single()["outputFile"] = output.name
        val updatedMetadata = JsonOutput.prettyPrint(JsonOutput.toJson(document)) + "\n"
        val temporaryMetadata = Files.createTempFile(metadata.parentFile.toPath(), "output-metadata-", ".tmp")
        try {
            Files.copy(source.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.writeString(temporaryMetadata, updatedMetadata)
            Files.move(temporaryMetadata, metadata.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.delete(source.toPath())
        } finally {
            Files.deleteIfExists(temporaryMetadata)
        }
    }
}
