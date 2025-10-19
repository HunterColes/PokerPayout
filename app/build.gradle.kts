import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.baseline.profile)
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
    versionCode = 14
    versionName = "1.1.2"
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
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
        jvmToolchain(17)
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
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

baselineProfile {
    dexLayoutOptimization = true
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

    implementation(libs.test.android.profile.installer)
    baselineProfile(project(":baseline-profiles"))

    ksp(libs.hilt.compiler)
    ksp(libs.room.compiler)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
