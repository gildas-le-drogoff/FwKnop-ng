// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects the startup profile: cold launch up to the config list, the screen opened to knock.
 *
 * Run with `./gradlew :app:generateBaselineProfile` on a device (API 33+, or rooted API 28+) where the
 * app lock is disabled. Adding a couple of configs beforehand also profiles the cards. No packet is
 * sent: the knock button is never pressed.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        awaitConfigList()
        scrollConfigs()
    }
}
