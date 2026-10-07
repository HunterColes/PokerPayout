import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

// Release keystore secrets: gitignored keystore.properties at the repo root (chmod 600).
// Falls back to RELEASE_* Gradle properties / ORG_GRADLE_PROJECT_RELEASE_* env vars.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun keystoreValue(key: String, legacyProperty: String): String? =
    keystoreProperties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: project.findProperty(legacyProperty) as String?

android {
    compileSdk = 34
    namespace = "com.huntercoles.pokerpayout"

    defaultConfig {
        applicationId = "com.huntercoles.pokerpayout"
        minSdk = 26
        targetSdk = 34
        versionCode = 40
        versionName = "1.3.14"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        buildConfig = true // the app module has no Compose code; the features and core do
    }

    lint {
        // Baseline and abortOnError come from the root build script.
        checkReleaseBuilds = false
    }

    signingConfigs {
        // Debug signing (always available)
        getByName("debug")

        // Production signing (only if keystore exists and properties are set)
        val storeFile = keystoreProperties.getProperty("storeFile")?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { rootProject.file(it) }
            ?: (project.findProperty("RELEASE_STORE_FILE") as String?)?.let { project.file(it) }
        val storePassword = keystoreValue("storePassword", "RELEASE_STORE_PASSWORD")
        val keyAlias = keystoreValue("keyAlias", "RELEASE_KEY_ALIAS")
        val keyPassword = keystoreValue("keyPassword", "RELEASE_KEY_PASSWORD")

        if (storeFile != null && storePassword != null && keyAlias != null && keyPassword != null) {
            create("release") {
                this.storeFile = storeFile
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            // R8 (shrink, optimize, obfuscate) and resource shrinking. R8's output is
            // deterministic for a pinned AGP + JDK, so F-Droid's rebuild still matches
            // byte for byte (release.sh --repro-check proves it for every release).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use production signing if available, otherwise use debug signing for development
            signingConfig = if (signingConfigs.names.contains("release")) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        // Same JDK in every module and on F-Droid's buildserver (Debian 13 ships only JDK 21).
        // The javac major version changes the APK bytes, so release builds must use 21 too.
        jvmToolchain(21)
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "assets/dexopt/baseline.prof"
            excludes += "assets/dexopt/baseline.profm"
            excludes += "**/baseline.prof"
            excludes += "**/baseline.profm"
            excludes += "META-INF/version-control-info.textproto"
        }
    }

    applicationVariants.all {
        val variantName = name
        val variantVersion = versionName ?: "unspecified"
        outputs
            .mapNotNull { it as? BaseVariantOutputImpl }
            .forEach { output ->
                output.outputFileName = "PokerPayout-v$variantVersion-$variantName.apk"
            }
    }
}

configurations {
    all {
        exclude(group = "androidx.profileinstaller", module = "profileinstaller")
    }
}

androidComponents {
    onVariants { variant ->
        // Transform merged assets to drop baseline profile artifacts before packaging/signing.
        val taskProvider = tasks.register(
            "strip${variant.name.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString() }}BaselineProfiles",
            StripBaselineProfilesTask::class.java
        )

        variant.artifacts
            .use(taskProvider)
            .wiredWithDirectories(
                StripBaselineProfilesTask::inputDir,
                StripBaselineProfilesTask::outputDir
            )
            .toTransform(SingleArtifact.ASSETS)
    }
}

tasks.configureEach {
    // Disable ArtProfile task graph entirely to avoid generating non-deterministic baseline files.
    if (name.contains("ArtProfile")) {
        enabled = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":tournament-feature"))
    implementation(project(":bank-feature"))
    implementation(project(":tools-feature"))

    implementation(libs.hilt)
    implementation(libs.timber)

    ksp(libs.hilt.compiler)
}

abstract class StripBaselineProfilesTask : DefaultTask() {
    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun strip() {
        val output = outputDir.asFile.get()
        project.delete(output)
        project.copy {
            from(inputDir)
            into(output)
            exclude("**/dexopt/baseline.prof", "**/dexopt/baseline.profm")
        }
    }
}
