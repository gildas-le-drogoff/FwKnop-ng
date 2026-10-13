package fr.gildasledrogoff.fwknop.presentation.ui.screens.configedit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.CryptoDraft
import fr.gildasledrogoff.fwknop.domain.model.DigestAlgorithm
import fr.gildasledrogoff.fwknop.domain.model.GpgKeyId
import fr.gildasledrogoff.fwknop.domain.model.GpgKeyRole
import fr.gildasledrogoff.fwknop.presentation.state.ConfigEditEvent
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.DropdownOptions
import fr.gildasledrogoff.fwknop.presentation.ui.components.LabeledSwitch
import fr.gildasledrogoff.fwknop.presentation.ui.components.OptionDropdown
import fr.gildasledrogoff.fwknop.presentation.ui.components.SecretTextField
import fr.gildasledrogoff.fwknop.presentation.ui.components.SectionCard
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Composable
fun CryptoSection(crypto: CryptoDraft, onEvent: (ConfigEditEvent) -> Unit) {
    val onChange: (CryptoDraft) -> Unit = { onEvent(ConfigEditEvent.CryptoChanged(it)) }
    SectionCard(stringResource(R.string.section_crypto), R.drawable.ic_key_rounded) {
        LabeledSwitch(
            stringResource(R.string.field_use_gpg),
            crypto.useGpg,
            { onChange(crypto.copy(useGpg = it)) })
        AnimatedVisibility(visible = crypto.useGpg) {
            GpgKeys(crypto) { onEvent(ConfigEditEvent.SelectGpgKey(it)) }
        }
        AnimatedVisibility(visible = !crypto.useGpg) { RijndaelKey(crypto, onChange) }
        HmacKey(crypto, onChange)
        DigestFields(crypto, onChange)
        LabeledSwitch(
            stringResource(R.string.field_legacy),
            crypto.legacy,
            { onChange(crypto.copy(legacy = it)) })
    }
}

@Composable
private fun RijndaelKey(crypto: CryptoDraft, onChange: (CryptoDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing)) {
        SecretTextField(
            crypto.key,
            { onChange(crypto.copy(key = it)) },
            stringResource(R.string.field_rijndael_key),
            supportingText = if (crypto.key.isEmpty()) stringResource(R.string.hint_blank_key) else null,
        )
        LabeledSwitch(
            label = stringResource(R.string.field_key_base64),
            checked = crypto.keyBase64,
            onCheckedChange = { onChange(crypto.copy(keyBase64 = it)) },
        )
    }
}

@Composable
private fun HmacKey(crypto: CryptoDraft, onChange: (CryptoDraft) -> Unit) {
    SecretTextField(
        crypto.hmac,
        { onChange(crypto.copy(hmac = it)) },
        stringResource(R.string.field_hmac_key)
    )
    LabeledSwitch(stringResource(R.string.field_hmac_base64), crypto.hmacBase64, {
        onChange(crypto.copy(hmacBase64 = it))
    })
}

@Composable
private fun DigestFields(crypto: CryptoDraft, onChange: (CryptoDraft) -> Unit) {
    val digests = DropdownOptions(
        stringResource(R.string.field_digest),
        DigestAlgorithm.entries,
        DigestAlgorithm::name
    )
    OptionDropdown(digests, crypto.digest, { onChange(crypto.copy(digest = it)) })
    val hmacs = digests.copy(label = stringResource(R.string.field_hmac_digest))
    OptionDropdown(hmacs, crypto.hmacDigest, { onChange(crypto.copy(hmacDigest = it)) })
}

@Composable
private fun GpgKeys(crypto: CryptoDraft, onSelect: (GpgKeyRole) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing)) {
        GpgKeyRow(stringResource(R.string.field_gpg_recipient), crypto.gpgRecipient) {
            onSelect(
                GpgKeyRole.RECIPIENT
            )
        }
        GpgKeyRow(
            stringResource(R.string.field_gpg_signer),
            crypto.gpgSigner
        ) { onSelect(GpgKeyRole.SIGNER) }
    }
}

@Composable
private fun GpgKeyRow(label: String, key: GpgKeyId?, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = key?.toHex() ?: stringResource(R.string.gpg_no_key),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onSelect) { Text(stringResource(R.string.action_select)) }
    }
}

@AdaptivePreviews
@Composable
private fun CryptoSectionPreview() {
    FwknopTheme { CryptoSection(CryptoDraft(key = "secret", hmac = "hmac"), {}) }
}

@AdaptivePreviews
@Composable
private fun CryptoSectionGpgPreview() {
    FwknopTheme {
        CryptoSection(
            CryptoDraft(useGpg = true, gpgRecipient = GpgKeyId(0x1234ABCDL)),
            {})
    }
}
