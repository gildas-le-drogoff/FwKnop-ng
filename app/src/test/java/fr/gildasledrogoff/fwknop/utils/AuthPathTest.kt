// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.utils
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.utils.helpers.AuthCapabilities
import fr.gildasledrogoff.fwknop.utils.helpers.AuthPath
import fr.gildasledrogoff.fwknop.utils.helpers.chooseAuthPath
import org.junit.Test
class AuthPathTest {
    @Test
    fun `API 30 and later use one prompt accepting biometric or credential`() {
        for (sdk in 30..37) {
            assertThat(chooseAuthPath(AuthCapabilities(sdk, deviceSecure = true, strongBiometric = false)))
                .isEqualTo(AuthPath.PROMPT_STRONG_OR_CREDENTIAL)
        }
    }
    @Test
    fun `API 26 to 29 with a strong biometric prompt for it then fall back to Keyguard`() {
        for (sdk in 26..29) {
            assertThat(chooseAuthPath(AuthCapabilities(sdk, deviceSecure = true, strongBiometric = true)))
                .isEqualTo(AuthPath.PROMPT_STRONG_THEN_KEYGUARD)
        }
    }
    @Test
    fun `API 26 to 29 without biometric go straight to Keyguard`() {
        for (sdk in 26..29) {
            assertThat(chooseAuthPath(AuthCapabilities(sdk, deviceSecure = true, strongBiometric = false)))
                .isEqualTo(AuthPath.KEYGUARD)
        }
    }
    @Test
    fun `a device without credential has nothing to authenticate against`() {
        assertThat(chooseAuthPath(AuthCapabilities(37, deviceSecure = false, strongBiometric = true)))
            .isEqualTo(AuthPath.UNAVAILABLE)
        assertThat(chooseAuthPath(AuthCapabilities(26, deviceSecure = false, strongBiometric = false)))
            .isEqualTo(AuthPath.UNAVAILABLE)
    }
}
