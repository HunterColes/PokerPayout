plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

android {
    compileSdk = 34
    namespace = "com.huntercoles.fatline.basicfeature"

    defaultConfig {
        minSdk = 26
    }

    testOptions {
        targetSdk = 34
    }

    buildFeatures {
        compose = true
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
        jvmToolchain(17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.coil)
    implementation(libs.compose.material3)
    implementation(libs.hilt)
    implementation(libs.kotlin.coroutines)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation)
    implementation(libs.navigation.hilt)
    implementation(libs.room)
    implementation(libs.timber)
    
    // Testing dependencies
    testImplementation(libs.bundles.common.test)
    testImplementation(libs.test.robolectric)
    testImplementation(libs.test.androidx.core)
    androidTestImplementation(libs.bundles.common.android.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.test.android.junit)
    debugImplementation(libs.debug.compose.manifest)

    ksp(libs.hilt.compiler)
}
