// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.baselineprofile)
}

val keystoreProperties =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

android {
    namespace = "fr.gildasledrogoff.fwknop"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.gildasledrogoff.fwknop"
        minSdk = 26
        targetSdk = 37
        versionCode = 41
        versionName = "2.2.0"
        testInstrumentationRunner = "fr.gildasledrogoff.fwknop.HiltTestRunner"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile").removePrefix("../"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            // x86/x86_64 would only serve emulators (debug keeps them): drops 4.3 MiB of SQLCipher binaries.
            //noinspection ChromeOsAbiSupport
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // org.openintents.openpgp.IOpenPgpService2 (vendored openpgp-api).
        aidl = true
    }

    testOptions {
        // Robolectric reads the merged manifest and resources (network security config).
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric (SDK 35+) reaches into java.io.FileDescriptor internals through SharedSecrets.
            it.jvmArgs(
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                // Robolectric loads native code (System.load) and protobuf-javalite uses sun.misc.Unsafe:
                // grant both explicitly so the JDK stops warning about them.
                "--enable-native-access=ALL-UNNAMED",
                "--sun-misc-unsafe-memory-access=allow",
            )
        }
    }

    dependenciesInfo {
        // Google-encrypted dependency metadata blob: flagged by the F-Droid scanner.
        includeInApk = false
        includeInBundle = false
    }

    androidResources {
        // Ship only our locales: strips the dozens of library translations from the APK.
        localeFilters += listOf("en", "fr")
    }

    packaging {
        // AGP strips native libraries only when an NDK is installed: never strip, so the APK bytes
        // do not depend on the build machine (F-Droid compares its build with the published APK).
        jniLibs.keepDebugSymbols += "**/*.so"
        resources.excludes +=
            listOf(
                "META-INF/DEPENDENCIES.txt",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE.txt",
                "META-INF/{AL2.0,LGPL2.1}",
                // dnsjava JDK 18+ resolver SPI: no such service on Android.
                "META-INF/services/java.net.spi.InetAddressResolverProvider",
            )
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

// runTest, TestScope, advanceUntilIdle...: experimental coroutine test APIs, opted into for test sources only.
tasks
    .withType<KotlinCompile>()
    .matching { it.name.contains("UnitTest") || it.name.contains("AndroidTest") }
    .configureEach { compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi") }

room {
    schemaDirectory("$projectDir/schemas")
}

baselineProfile {
    // Generated on demand (./gradlew :app:generateBaselineProfile), then committed under src/release.
    automaticGenerationDuringBuild = false
    dexLayoutOptimization = true
    // The plugin also provides nonMinifiedRelease: release-shaped (release ABIs, release signing when the
    // keystore is present) but without R8/resource shrinking. Use it for fast manual testing:
    // ./gradlew :app:installNonMinifiedRelease
}

androidComponents {
    // The build types created by the baseline profile plugin are cloned from release: without a release
    // keystore, sign them with the debug key so the macrobenchmarks can install them.
    onVariants(selector().all()) { variant ->
        val benchmarkType = variant.buildType == "benchmarkRelease" || variant.buildType == "nonMinifiedRelease"
        if (benchmarkType && keystoreProperties.isEmpty) {
            variant.signingConfig.from(android.signingConfigs.getByName("debug"))
        }
    }
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.viewmodel.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.tink.android)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.commons.validator) {
        // Only the standalone validators in org.apache.commons.validator.routines are used.
        exclude(group = "commons-beanutils")
        exclude(group = "commons-collections")
        exclude(group = "commons-digester")
        exclude(group = "commons-logging")
    }
    implementation(libs.dnsjava)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.uiautomator)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.room.testing)
    kspAndroidTest(libs.hilt.compiler)
}

val ktlintConfigOverride =
    mapOf<String, Any>(
        "ktlint_standard_no-wildcard-imports" to "disabled",
        "ktlint_standard_filename" to "disabled",
    )
