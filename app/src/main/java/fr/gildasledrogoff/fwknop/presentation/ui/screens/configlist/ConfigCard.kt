package fr.gildasledrogoff.fwknop.presentation.ui.screens.configlist

import android.nfc.NfcAdapter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.state.ConfigSummary
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.FwknopIcon
import fr.gildasledrogoff.fwknop.presentation.ui.components.LargeFontPreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.labelRes
import fr.gildasledrogoff.fwknop.presentation.ui.components.scaledMaxLines
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
)

enum class KnockVisual { IDLE, SENDING, SENT }


@Immutable
data class KnockActivity(val sending: String? = null, val sent: String? = null) {
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
) {
    val dimens = DimensTheme.dimens
    val accent = configAccent(summary.nickname)
    val editLabel = stringResource(R.string.a11y_edit_config)
    ElevatedCard(
        onClick = { actions.onEdit(summary.nickname) },
        // TalkBack: "double tap to edit" rather than a bare "double tap to activate".
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
            modifier = Modifier
                .accentEdge(accent)
                .padding(dimens.cardPadding),
        ) {
            ConfigCardHeader(summary, accent, actions)
            ConfigTags(summary)
            KnockButton(summary.nickname, knock, actions.onKnock)
        }
    }
}


private fun Modifier.accentEdge(accent: Color): Modifier = drawWithCache {
    val wash = Brush.verticalGradient(listOf(accent.copy(alpha = 0.12f), Color.Transparent), endY = 96.dp.toPx())
    val stripe = Size(4.dp.toPx(), size.height)
    onDrawBehind {
        drawRect(wash)
        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - stripe.width else 0f
        drawRect(accent, topLeft = Offset(x, 0f), size = stripe)
    }
}

@Composable
private fun ConfigCardHeader(summary: ConfigSummary, accent: Color, actions: ConfigCardActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FwknopIcon(
            R.drawable.ic_shield_rounded_fill,
            null,
            tint = accent
        )
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
                // Lets TalkBack users jump from card to card by heading.
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
        ConfigCardMenu(summary.nickname, actions)
    }
}

@Composable
private fun ConfigTags(summary: ConfigSummary) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing),
        verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing),
    ) {
        // Bare "UDP" or "tcp/22" mean little when read out: TalkBack gets the field name too.
        val protocol = summary.protocol.name
        Tag(protocol, stringResource(R.string.a11y_tag_protocol, protocol))
        Tag(stringResource(summary.messageType.labelRes()))
        if (summary.accessDetail.isNotEmpty()) {
            Tag(summary.accessDetail, stringResource(R.string.a11y_tag_access, summary.accessDetail))
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
                .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        )
    }
}

@Composable
private fun KnockButton(nickname: String, status: KnockVisual, onKnock: (String) -> Unit) {
    // Every card has a "Knock" button: TalkBack must say which config it knocks, and its progress.
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
            KnockVisual.SENDING -> progress.animateTo(1f, infiniteRepeatable(tween(1400, easing = LinearEasing)))
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
            val f = if (looping) (progress.value + i.toFloat() / rings) % 1f else progress.value - i * 0.25f
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
private fun ConfigCardMenu(nickname: String, actions: ConfigCardActions) {
    var expanded by remember { mutableStateOf(false) }
    val nfcAvailable = rememberNfcAvailable()
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
            MenuItem(
                R.string.action_export_qr,
                R.drawable.ic_qr_code_rounded
            ) { close(); actions.onExportQr(nickname) }
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
            MenuItem(
                R.string.action_diagnose,
                R.drawable.ic_bolt_rounded
            ) { close(); actions.onDiagnose(nickname) }
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
    FwknopTheme(darkTheme = true) { ConfigCard(previewSummaries[1], previewActions, knock = KnockVisual.SENT) }
}

@LargeFontPreviews
@Composable
private fun ConfigCardLargeFontPreview() {
    FwknopTheme { ConfigCard(previewSummaries[1], previewActions) }
}
