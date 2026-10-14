// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.AutoLockDelay
import fr.gildasledrogoff.fwknop.presentation.state.SecuritySettingsEvent
import fr.gildasledrogoff.fwknop.presentation.state.SecuritySettingsState
import fr.gildasledrogoff.fwknop.presentation.ui.components.*
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Immutable
data class AutoLockField(val value: AutoLockDelay, val onChange: (AutoLockDelay) -> Unit)

@Composable
fun SecuritySection(
    security: SecuritySettingsState,
    autoLock: AutoLockField,
    onEvent: (SecuritySettingsEvent) -> Unit,
) {
    SectionCard(stringResource(R.string.section_security), R.drawable.ic_lock_rounded) {
        AppLockSwitch(security, onEvent)
        AutoLockDropdown(autoLock, enabled = security.lockEnabled && !security.busy)
        RotateKeysRow(busy = security.busy) { onEvent(SecuritySettingsEvent.RotateKeysRequested) }
    }
}

@Composable
private fun RotateKeysRow(busy: Boolean, onRotate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing)) {
        Text(
            text = stringResource(R.string.hint_rotate_keys),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onRotate,
            enabled = !busy
        ) { Text(stringResource(R.string.action_rotate_keys)) }
    }
}

@Composable
private fun AppLockSwitch(
    security: SecuritySettingsState,
    onEvent: (SecuritySettingsEvent) -> Unit
) {
    val available = security.canEnableLock || security.lockEnabled
    LabeledSwitch(
        label = stringResource(R.string.field_app_lock),
        checked = security.lockEnabled,
        onCheckedChange = { onEvent(SecuritySettingsEvent.LockToggled(it)) },
        supportingText = stringResource(if (available) R.string.hint_app_lock else R.string.hint_app_lock_unavailable),
        enabled = available && !security.busy,
    )
}

@Composable
private fun AutoLockDropdown(field: AutoLockField, enabled: Boolean) {
    val labels = AutoLockDelay.entries.associateWith { stringResource(it.label) }
    OptionDropdown(
        spec = DropdownOptions(
            stringResource(R.string.field_auto_lock),
            AutoLockDelay.entries
        ) { labels.getValue(it) },
        selected = field.value,
        onSelected = field.onChange,
        enabled = enabled,
    )
}

@get:StringRes
private val AutoLockDelay.label: Int
    get() = when (this) {
        AutoLockDelay.NEVER -> R.string.auto_lock_never
        AutoLockDelay.SECONDS_30 -> R.string.auto_lock_30s
        AutoLockDelay.MINUTE_1 -> R.string.auto_lock_1m
        AutoLockDelay.MINUTES_5 -> R.string.auto_lock_5m
        AutoLockDelay.MINUTES_15 -> R.string.auto_lock_15m
    }

@AdaptivePreviews
@Composable
private fun SecuritySectionPreview() {
    FwknopTheme {
        SecuritySection(
            security = SecuritySettingsState(lockEnabled = true, canEnableLock = true),
            autoLock = AutoLockField(AutoLockDelay.MINUTE_1) {},
            onEvent = {},
        )
    }
}
