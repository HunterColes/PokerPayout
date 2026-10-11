plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.junit)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

android {
    compileSdk = 34
    namespace = "com.huntercoles.pokerpayout.bank"

    with (defaultConfig) {
        minSdk = 26
        targetSdk = 34
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Robolectric needs the merged resources (fonts, strings) to render composables.
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(21)

        compilerOptions {
            freeCompilerArgs.addAll(
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
            )
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.hilt)
    implementation(libs.kotlin.coroutines)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation)
    implementation(libs.navigation.hilt)
    testImplementation(libs.bundles.common.test)
    testImplementation(libs.bundles.screenshot.test)
    testImplementation(testFixtures(project(":core"))) // device matrix, layout checks, goldens
    // PP-111: the clock's big moments from the Bank's own knockouts, Undo and restarts (BankBigMomentsTest)
    testImplementation(project(":tournament-feature"))
    testImplementation(libs.test.robolectric)
    testImplementation(libs.test.androidx.core)
    debugImplementation(libs.debug.compose.manifest)

    ksp(libs.hilt.compiler)
}

// Screenshot goldens under src/test/screenshots, verified by testDebugUnitTest and re-recorded with
// recordRoborazziDebug, as in core. See docs/TESTING.md, section 9.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}
