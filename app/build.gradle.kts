import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    // Baseline profile plugin disabled for F-Droid reproducible builds
    // alias(libs.plugins.baseline.profile)
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

android {
    compileSdk = 34
    namespace = "com.huntercoles.pokerpayout"

    defaultConfig {
        applicationId = "com.huntercoles.pokerpayout"
        minSdk = 26
        targetSdk = 34
        versionCode = 21
        versionName = "1.1.9"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    signingConfigs {
        // Debug signing (always available)
        getByName("debug")

        // Production signing (only if keystore exists and properties are set)
        val storeFile = project.findProperty("RELEASE_STORE_FILE") as String?
        val storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String?
        val keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String?
        val keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?

        if (storeFile != null && storePassword != null && keyAlias != null && keyPassword != null) {
            create("release") {
                this.storeFile = project.file(storeFile)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // proguardFiles(
            //     getDefaultProguardFile("proguard-android.txt"),
            //     "proguard-rules.pro"
            // )
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
        jvmToolchain(17)
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
    implementation(libs.navigation) // needed for Room
    implementation(libs.room.ktx)
    implementation(libs.timber)

    // Baseline profiles disabled for F-Droid reproducible builds
    // implementation(libs.test.android.profile.installer)
    // baselineProfile(project(":baseline-profiles"))

    ksp(libs.hilt.compiler)
    ksp(libs.room.compiler)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
