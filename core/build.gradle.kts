plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.junit)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

android {
    compileSdk = 34
    namespace = "com.huntercoles.pokerpayout.core"

    with (defaultConfig) {
        minSdk = 26
        targetSdk = 34
    }

    buildFeatures {
        compose = true
    }

    // Shared screenshot-test kit (device matrix, layout assertions, golden paths) in
    // src/testFixtures. Feature modules use it with testImplementation(testFixtures(project(":core"))).
    testFixtures {
        enable = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources (fonts, strings) to render composables.
            isIncludeAndroidResources = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("proguard-rules.pro")
        }
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
                "-opt-in=kotlinx.coroutines.FlowPreview",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
            )
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.hilt)
    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlin.serialization)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation)
    implementation(libs.timber)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.debug.compose.ui.tooling)
    debugImplementation(libs.debug.compose.manifest) // ComponentActivity for Compose tests on Robolectric
    testImplementation(libs.bundles.common.test)
    testImplementation(libs.bundles.screenshot.test)
    testImplementation(libs.test.androidx.core)
    testImplementation(libs.test.kotest.property)
    androidTestImplementation(libs.bundles.common.android.test)

    testFixturesImplementation(platform(libs.compose.bom))
    testFixturesImplementation(libs.compose.material3)
    testFixturesApi(libs.test.junit4)
    testFixturesApi(libs.bundles.screenshot.test)

    ksp(libs.hilt.compiler)
    kspAndroidTest(libs.hilt.compiler)
}

// Screenshot goldens are committed under src/test/screenshots. ./gradlew testDebugUnitTest verifies
// them (roborazzi.test.verify=true in gradle.properties); ./gradlew recordRoborazziDebug re-records
// them deliberately. See docs/TESTING.md, section 9.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}
