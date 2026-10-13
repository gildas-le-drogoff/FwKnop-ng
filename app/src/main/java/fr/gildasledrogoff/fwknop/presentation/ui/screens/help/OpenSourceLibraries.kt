package fr.gildasledrogoff.fwknop.presentation.ui.screens.help

data class OpenSourceLibrary(
    val name: String,
    val license: String,
    val url: String,
    val notice: String? = null,
)

private const val APACHE_2 = "Apache License 2.0"
val openSourceLibraries = listOf(
    OpenSourceLibrary(
        "AndroidX, Jetpack Compose, Room",
        APACHE_2,
        "https://developer.android.com/jetpack/androidx"
    ),
    OpenSourceLibrary("Dagger / Hilt", APACHE_2, "https://github.com/google/dagger"),
    OpenSourceLibrary(
        "Kotlin, kotlinx.coroutines, kotlinx.serialization",
        APACHE_2,
        "https://github.com/JetBrains/kotlin"
    ),
    OpenSourceLibrary("Tink", APACHE_2, "https://github.com/tink-crypto/tink-java"),
    OpenSourceLibrary(
        name = "SQLCipher for Android",
        license = "BSD (Zetetic)",
        url = "https://www.zetetic.net/sqlcipher/license/",
        notice = "Copyright (c) Zetetic LLC. Includes SQLite and LibTomCrypt (public domain).",
    ),
    OpenSourceLibrary("OkHttp, Okio", APACHE_2, "https://github.com/square/okhttp"),
    OpenSourceLibrary(
        name = "dnsjava",
        license = "BSD-3-Clause",
        url = "https://github.com/dnsjava/dnsjava",
        notice = "Copyright (c) 1998-2019, Brian Wellington. Copyright (c) 2005 VeriSign. " +
                "Copyright (c) 2019-2023, dnsjava authors.",
    ),
    OpenSourceLibrary(
        "SLF4J API",
        "MIT",
        "https://www.slf4j.org",
        "Copyright (c) 2004-2017 QOS.ch."
    ),
    OpenSourceLibrary(
        name = "Apache Commons Validator",
        license = APACHE_2,
        url = "https://commons.apache.org/proper/commons-validator/",
        notice = "Copyright 2002-2026 The Apache Software Foundation. This product includes software developed " +
                "at The Apache Software Foundation (https://www.apache.org/).",
    ),
    OpenSourceLibrary("ZXing", APACHE_2, "https://github.com/zxing/zxing"),
    OpenSourceLibrary(
        "ZXing Android Embedded",
        APACHE_2,
        "https://github.com/journeyapps/zxing-android-embedded"
    ),
    OpenSourceLibrary(
        "OpenPGP API (OpenKeychain)",
        APACHE_2,
        "https://github.com/open-keychain/openpgp-api"
    ),
    OpenSourceLibrary("Gson", APACHE_2, "https://github.com/google/gson"),
    OpenSourceLibrary(
        "Material Symbols",
        APACHE_2,
        "https://github.com/google/material-design-icons"
    ),
)
