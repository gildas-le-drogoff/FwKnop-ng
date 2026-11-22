// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.components

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class SecretTextFieldTest {
    @get:Rule
    val rule = createComposeRule()

    private val passwordField = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)
    private val obfuscated = SemanticsMatcher.expectValue(SemanticsProperties.IsPasswordObfuscated, true)
    private val revealed = SemanticsMatcher.expectValue(SemanticsProperties.IsPasswordObfuscated, false)

    @Test
    fun typingNotifiesOnValueChange() {
        var value by mutableStateOf("")
        val received = mutableListOf<String>()
        rule.setContent {
            FwknopTheme { SecretTextField(value, { value = it; received += it }, "Key") }
        }

        rule.onNode(passwordField).performTextInput("abc")
        rule.waitForIdle()

        assertThat(received.last()).isEqualTo("abc")
        assertThat(value).isEqualTo("abc")
    }

    @Test
    fun externalValueChangeIsReflectedInField() {
        var value by mutableStateOf("first")
        rule.setContent {
            FwknopTheme { SecretTextField(value, { value = it }, "Key") }
        }
        rule.onNode(passwordField).assertTextContains("first")

        value = "second"
        rule.waitForIdle()

        rule.onNode(passwordField).assertTextContains("second")
    }

    @Test
    fun toggleRevealsThenHidesSecret() {
        rule.setContent {
            FwknopTheme { SecretTextField("secret", {}, "Key") }
        }
        rule.onNode(passwordField and obfuscated).assertExists()

        rule.onNodeWithContentDescription("Show secret").performClick()
        rule.onNode(passwordField and revealed).assertExists()

        rule.onNodeWithContentDescription("Hide secret").performClick()
        rule.onNode(passwordField and obfuscated).assertExists()
    }
}
