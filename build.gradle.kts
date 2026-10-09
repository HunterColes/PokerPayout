plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.junit) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.roborazzi) apply false
}

allprojects {
    apply(
        plugin = "io.gitlab.arturbosch.detekt"
    )

    detekt {
        buildUponDefaultConfig = true
        config.setFrom(files("$rootDir/gradle/detekt.yml"))
        // Pre-existing findings live in each module's detekt-baseline.xml, so only new ones fail
        // the build. Regenerate with ./gradlew detektBaseline after fixing old findings.
        baseline = file("detekt-baseline.xml")
    }

    // Same for Android Lint: errors not in the module's lint-baseline.xml fail the build.
    // Regenerate with ./gradlew updateLintBaseline.
    listOf("com.android.application", "com.android.library").forEach { pluginId ->
        plugins.withId(pluginId) {
            (extensions.getByName("android") as com.android.build.api.dsl.CommonExtension<*, *, *, *, *, *>).lint {
                baseline = file("lint-baseline.xml")
                abortOnError = true
                // Toolchain noise, not code findings: "a newer version of X is available" changes
                // with every upstream release and needs the network, and ObsoleteLintCustomCheck
                // (library lint jars built for an older lint) points into build/intermediates, so it
                // can't be baselined. The toolchain refresh (PP-023) deals with both.
                disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "ObsoleteLintCustomCheck")
            }
        }
    }
    
    // Kotlin bytecode targets 17 in every module (the toolchain is JDK 21; see CLAUDE.md).
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

buildscript {
    dependencies {
        classpath(libs.compose.rules)
    }
}

// The Compose compiler, in every module that has Compose code.
// - gradle/compose-stability.conf: types it treats as stable (read-only collections).
// - Stability reports, on demand only: `-PcomposeReports` (the measure job in
//   .github/workflows/device.yml passes it). Each module writes build/compose-reports/ (which
//   classes are stable, which composables skip) and build/compose-metrics/. Off in every other
//   build, so release builds and F-Droid's rebuild never see it.
val composeReports = providers.gradleProperty("composeReports").isPresent
subprojects {
    plugins.withId("org.jetbrains.kotlin.plugin.compose") {
        extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFile.set(rootProject.layout.projectDirectory.file("gradle/compose-stability.conf"))
            if (composeReports) {
                reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
                metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
            }
        }
    }
}

// Unit tests. Every module with tests applies the android-junit5 plugin, so its test tasks run on
// the JUnit Platform with both engines from the common-test bundle: Jupiter for JUnit 5 tests and
// Vintage for JUnit 4 tests (which includes every Robolectric test). See docs/TESTING.md.
subprojects {
    val testSourceDir = layout.projectDirectory.dir("src/test")
    tasks.withType<Test>().configureEach {
        // Same number formatting on every machine and in CI. A test about another locale must
        // set Locale itself.
        systemProperty("user.language", "en")
        systemProperty("user.country", "US")

        testLogging {
            events(
                org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
                org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
            )
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }

        // Guard against tests that silently never run (until v1.2.0, 54 of them didn't because of
        // a JUnit 4/5 engine mismatch). Fail if this task executed fewer tests than src/test
        // declares @Test methods. Skipped (@Disabled/@Ignore) tests count as executed.
        var executed = 0L
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) = Unit
            override fun beforeTest(testDescriptor: TestDescriptor) = Unit
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) = Unit
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent == null) executed = result.testCount
            }
        })
        doLast {
            val filtered = filter.includePatterns.isNotEmpty() ||
                gradle.startParameter.taskRequests.any { "--tests" in it.args }
            if (filtered) return@doLast
            val testAnnotation = Regex("""^\s*@(org\.junit\.(jupiter\.api\.)?|kotlin\.test\.)?Test\b.*""")
            val declared = testSourceDir.asFileTree
                .matching { include("**/*.kt", "**/*.java") }
                .sumOf { file -> file.readLines().count { testAnnotation.matches(it) } }
            if (executed < declared) {
                throw GradleException(
                    "$path ran $executed tests but src/test declares $declared @Test methods. " +
                        "Some tests were not discovered: check that the module applies the " +
                        "android-junit5 plugin and uses the common-test bundle (Jupiter + Vintage)."
                )
            }
        }
    }
}
