plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.junit)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.parcelize)
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

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(libs.timber)
    testImplementation(libs.bundles.common.test)
    testImplementation(libs.bundles.screenshot.test)
    testImplementation(testFixtures(project(":core"))) // device matrix, layout checks, goldens
    testImplementation(libs.test.robolectric)
    testImplementation(libs.test.androidx.core)
    androidTestImplementation(libs.bundles.common.android.test)
    debugImplementation(libs.debug.compose.manifest)

    ksp(libs.hilt.compiler)
    kspAndroidTest(libs.hilt.compiler)
}

// Screenshot goldens under src/test/screenshots, verified by testDebugUnitTest and re-recorded with
// recordRoborazziDebug, as in core. See docs/TESTING.md, section 9.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}
