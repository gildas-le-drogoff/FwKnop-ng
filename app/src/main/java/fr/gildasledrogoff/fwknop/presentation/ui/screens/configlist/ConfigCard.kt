// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.configlist

import android.nfc.NfcAdapter
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.ConfigKind
import fr.gildasledrogoff.fwknop.presentation.state.ConfigSummary
import fr.gildasledrogoff.fwknop.presentation.ui.components.*
import fr.gildasledrogoff.fwknop.presentation.ui.theme.ConfigAccent
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.configAccent

@Immutable
data class ConfigCardActions(
    val onKnock: (String) -> Unit,
    val onEdit: (String) -> Unit,
    val onDuplicate: (String) -> Unit,
    val onWriteNfc: (String) -> Unit,
    val onExportQr: (String) -> Unit,
    val onExportFile: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onDiagnose: (String) -> Unit = {},
    val onWake: (String) -> Unit = {},
)

enum class KnockVisual { IDLE, SENDING, SENT }

@Immutable
data class KnockActivity(
    val sending: String? = null,
    val sent: String? = null,
    val waking: String? = null,
) {
    fun of(nickname: String): KnockVisual = when (nickname) {
        sending -> KnockVisual.SENDING
        sent -> KnockVisual.SENT
        else -> KnockVisual.IDLE
    }
}

@Composable
fun ConfigCard(
    summary: ConfigSummary,
    actions: ConfigCardActions,
    modifier: Modifier = Modifier,
    knock: KnockVisual = KnockVisual.IDLE,
    waking: Boolean = false,
) {
    val dimens = DimensTheme.dimens
    val accent = configAccent(summary.nickname)
    val editLabel = stringResource(R.string.a11y_edit_config)
    ElevatedCard(
        onClick = { actions.onEdit(summary.nickname) },

        modifier = modifier
            .fillMaxWidth()
            .semantics {
                onClick(label = editLabel) {
                    actions.onEdit(summary.nickname)
                    true
                }
            },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
            modifier = Modifier.padding(dimens.cardPadding),
        ) {
            ConfigCardHeader(summary, accent, actions)
            ConfigTags(summary)
            if (summary.kind == ConfigKind.WAKE_ON_LAN) {
                WakeButton(summary.nickname, waking, actions.onWake)
            } else {
                KnockButton(summary.nickname, knock, actions.onKnock)
            }
        }
    }
}

@Composable
private fun ConfigCardHeader(
    summary: ConfigSummary,
    accent: ConfigAccent,
    actions: ConfigCardActions
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = accent.container, contentColor = accent.content) {
            FwknopIcon(
                if (summary.kind == ConfigKind.WAKE_ON_LAN) R.drawable.ic_power_rounded else R.drawable.ic_shield_rounded_fill,
                null,
                modifier = Modifier.padding(8.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = DimensTheme.dimens.itemSpacing),
        ) {
            Text(
                text = summary.nickname,
                style = MaterialTheme.typography.titleLarge,
                maxLines = scaledMaxLines(1),
                overflow = TextOverflow.Ellipsis,

                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = summary.endpoint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = scaledMaxLines(1),
                overflow = TextOverflow.Ellipsis,
            )
        }
        ConfigCardMenu(summary.nickname, summary.kind, actions)
    }
}

@Composable
private fun ConfigTags(summary: ConfigSummary) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing),
        verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing),
    ) {

        when (summary.kind) {
            ConfigKind.FWKNOP -> {
                val protocol = summary.protocol.name
                Tag(protocol, stringResource(R.string.a11y_tag_protocol, protocol))
                Tag(stringResource(summary.messageType.labelRes()))
            }

            ConfigKind.PORT_KNOCK -> Tag(stringResource(R.string.tag_port_knocking))
            ConfigKind.WAKE_ON_LAN -> Tag(stringResource(R.string.tag_wake_on_lan))
        }
        if (summary.accessDetail.isNotEmpty()) {
            Tag(
                summary.accessDetail,
                stringResource(R.string.a11y_tag_access, summary.accessDetail)
            )
        }
        if (summary.launchesApp) Tag(stringResource(R.string.tag_launches_app))
    }
}

@Composable
private fun Tag(text: String, description: String? = null) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = scaledMaxLines(1),
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = DimensTheme.dimens.itemSpacing / 2)
                .then(if (description != null) Modifier.semantics {
                    contentDescription = description
                } else Modifier),
        )
    }
}

@Composable
private fun KnockButton(nickname: String, status: KnockVisual, onKnock: (String) -> Unit) {

    val description = stringResource(R.string.a11y_knock_config, nickname)
    val progress = when (status) {
        KnockVisual.IDLE -> null
        KnockVisual.SENDING -> stringResource(R.string.a11y_knock_sending)
        KnockVisual.SENT -> stringResource(R.string.a11y_knock_sent)
    }
    Button(
        onClick = { onKnock(nickname) },
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = description
                progress?.let { stateDescription = it }
            }
            .knockRipples(status, MaterialTheme.colorScheme.primary),
    ) {
        KnockIcon(sent = status == KnockVisual.SENT)
        Text(stringResource(R.string.knock))
    }
}

@Composable
private fun WakeButton(nickname: String, waking: Boolean, onWake: (String) -> Unit) {
    val description = stringResource(R.string.a11y_wake_config, nickname)
    Button(
        onClick = { onWake(nickname) },
        enabled = !waking,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    ) {
        val iconModifier = Modifier.padding(end = ButtonDefaults.IconSpacing)
        if (waking) {
            CircularProgressIndicator(
                iconModifier.size(ButtonDefaults.IconSize),
                strokeWidth = 2.dp
            )
        } else {
            FwknopIcon(R.drawable.ic_power_rounded, null, iconModifier)
        }
        Text(stringResource(R.string.action_wake))
    }
}

@Composable
private fun KnockIcon(sent: Boolean) {
    AnimatedContent(
        targetState = sent,
        transitionSpec = {
            (scaleIn(initialScale = 0.3f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.3f) + fadeOut()) using
                    SizeTransform(clip = false)
        },
        label = "knockIcon",
    ) { done ->
        val turn by transition.animateFloat(label = "turn") { if (it == EnterExitState.Visible) 0f else -120f }
        FwknopIcon(
            if (done) R.drawable.ic_check_rounded else R.drawable.ic_bolt_rounded_fill,
            null,
            Modifier
                .padding(end = ButtonDefaults.IconSpacing)
                .graphicsLayer { rotationZ = turn },
        )
    }
}

@Composable
private fun Modifier.knockRipples(status: KnockVisual, color: Color): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(status) {
        progress.snapTo(0f)
        when (status) {
            KnockVisual.SENDING -> progress.animateTo(
                1f,
                infiniteRepeatable(tween(1400, easing = LinearEasing))
            )

            KnockVisual.SENT -> progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            KnockVisual.IDLE -> Unit
        }
    }
    if (status == KnockVisual.IDLE) return this
    val looping = status == KnockVisual.SENDING
    return drawBehind {
        val rings = if (looping) 3 else 2
        val start = size.height / 2
        val end = size.width * 0.6f
        for (i in 0 until rings) {
            val f =
                if (looping) (progress.value + i.toFloat() / rings) % 1f else progress.value - i * 0.25f
            if (f <= 0f || f >= 1f) continue
            drawCircle(
                color = color.copy(alpha = (1f - f) * 0.45f),
                radius = start + (end - start) * f,
                style = Stroke(width = (1f - f) * 3.dp.toPx() + 1f),
            )
        }
    }
}

@Composable
private fun ConfigCardMenu(nickname: String, kind: ConfigKind, actions: ConfigCardActions) {
    var expanded by remember { mutableStateOf(false) }
    val nfcAvailable = rememberNfcAvailable()

    val knocks = kind != ConfigKind.WAKE_ON_LAN
    Box {
        IconButton(onClick = { expanded = true }) {
            FwknopIcon(
                R.drawable.ic_more_vert_rounded,
                stringResource(R.string.action_more, nickname)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val close = { expanded = false }
            MenuItem(R.string.action_edit, R.drawable.ic_edit_rounded) {
                close(); actions.onEdit(
                nickname
            )
            }
            MenuItem(
                R.string.action_duplicate,
                R.drawable.ic_content_copy_rounded
            ) { close(); actions.onDuplicate(nickname) }
            if (knocks) {
                MenuItem(
                    R.string.action_export_qr,
                    R.drawable.ic_qr_code_rounded
                ) { close(); actions.onExportQr(nickname) }
            }
            MenuItem(
                R.string.action_export_file,
                R.drawable.ic_download_rounded
            ) { close(); actions.onExportFile(nickname) }
            if (nfcAvailable) {
                MenuItem(
                    R.string.action_write_nfc,
                    R.drawable.ic_nfc_rounded
                ) { close(); actions.onWriteNfc(nickname) }
            }
            if (knocks) {
                MenuItem(
                    R.string.action_diagnose,
                    R.drawable.ic_bolt_rounded
                ) { close(); actions.onDiagnose(nickname) }
            }
            MenuItem(
                R.string.action_delete,
                R.drawable.ic_delete_rounded
            ) { close(); actions.onDelete(nickname) }
        }
    }
}

@Composable
private fun MenuItem(label: Int, icon: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { FwknopIcon(icon, null) },
        onClick = onClick,
    )
}

@Composable
private fun rememberNfcAvailable(): Boolean {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    return remember(context) { !inPreview && NfcAdapter.getDefaultAdapter(context) != null }
}

internal val previewActions = ConfigCardActions({}, {}, {}, {}, {}, {}, {})

@AdaptivePreviews
@Composable
private fun ConfigCardPreview() {
    FwknopTheme { ConfigCard(previewSummaries.first(), previewActions) }
}

@AdaptivePreviews
@Composable
private fun ConfigCardSentPreview() {
    FwknopTheme(darkTheme = true) {
        ConfigCard(
            previewSummaries[1],
            previewActions,
            knock = KnockVisual.SENT
        )
    }
}

@LargeFontPreviews
@Composable
private fun ConfigCardLargeFontPreview() {
    FwknopTheme { ConfigCard(previewSummaries[1], previewActions) }
}
