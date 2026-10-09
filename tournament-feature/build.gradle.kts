plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt)
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
    namespace = "com.huntercoles.pokerpayout.tournament"

    defaultConfig {
        minSdk = 26
    }

    testOptions {
        targetSdk = 34
        // Robolectric needs the merged manifest to start the Compose test activity
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(21)
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
    
    // Testing dependencies
    testImplementation(libs.bundles.common.test)
    testImplementation(libs.bundles.screenshot.test)
    testImplementation(testFixtures(project(":core"))) // device matrix, layout checks, goldens
    testImplementation(libs.test.robolectric)
    testImplementation(libs.test.android.compose)
    testImplementation(libs.test.androidx.core)
    androidTestImplementation(libs.bundles.common.android.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.test.android.junit)
    debugImplementation(libs.debug.compose.manifest)

    ksp(libs.hilt.compiler)
}

// Screenshot goldens under src/test/screenshots, verified by testDebugUnitTest and re-recorded with
// recordRoborazziDebug, as in core. See docs/TESTING.md, section 9.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}
