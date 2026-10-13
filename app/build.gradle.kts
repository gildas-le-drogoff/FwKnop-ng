import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

val keystoreProperties = Properties().apply {
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
        versionCode = 37
        versionName = "2.0.0"
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // org.openintents.openpgp.IOpenPgpService2 (vendored openpgp-api).
        aidl = true
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
        resources.excludes += listOf(
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
        jvmTarget = JvmTarget.JVM_21
    }
}

room {
    schemaDirectory("$projectDir/schemas")
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
