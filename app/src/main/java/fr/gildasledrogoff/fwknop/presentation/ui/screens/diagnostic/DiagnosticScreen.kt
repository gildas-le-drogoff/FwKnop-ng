package fr.gildasledrogoff.fwknop.presentation.ui.screens.diagnostic

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.AddressFamily
import fr.gildasledrogoff.fwknop.domain.model.KnockTarget
import fr.gildasledrogoff.fwknop.domain.model.Protocol
import fr.gildasledrogoff.fwknop.domain.model.ResolvedSourceIp
import fr.gildasledrogoff.fwknop.domain.model.SourceIpMethod
import fr.gildasledrogoff.fwknop.presentation.state.DiagnosticDetail
import fr.gildasledrogoff.fwknop.presentation.state.DiagnosticEffect
import fr.gildasledrogoff.fwknop.presentation.state.DiagnosticEvent
import fr.gildasledrogoff.fwknop.presentation.state.DiagnosticPhase
import fr.gildasledrogoff.fwknop.presentation.state.DiagnosticState
import fr.gildasledrogoff.fwknop.presentation.state.DiagnosticStep
import fr.gildasledrogoff.fwknop.presentation.state.StepStatus
import fr.gildasledrogoff.fwknop.presentation.state.UiError
import fr.gildasledrogoff.fwknop.presentation.state.text
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.CollectEffects
import fr.gildasledrogoff.fwknop.presentation.ui.components.ErrorDialog
import fr.gildasledrogoff.fwknop.presentation.ui.components.FormTextField
import fr.gildasledrogoff.fwknop.presentation.ui.components.FwknopIcon
import fr.gildasledrogoff.fwknop.presentation.ui.components.SecretTextField
import fr.gildasledrogoff.fwknop.presentation.ui.components.SectionCard
import fr.gildasledrogoff.fwknop.presentation.ui.components.description
import fr.gildasledrogoff.fwknop.presentation.ui.components.rememberInteractionLauncher
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme
import fr.gildasledrogoff.fwknop.presentation.viewmodel.DiagnosticViewModel

@Composable
fun DiagnosticScreen(onBack: () -> Unit, viewModel: DiagnosticViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launchInteraction = rememberInteractionLauncher { id, completed, data ->
        viewModel.onEvent(DiagnosticEvent.InteractionFinished(id, completed, data))
    }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is DiagnosticEffect.LaunchInteraction ->
                viewModel.intentSender(effect.id)?.let { launchInteraction(effect.id, it) }
        }
    }
    DiagnosticScaffold(state.nickname, onBack) {
        if (!state.loading) DiagnosticContent(state, viewModel::onEvent)
    }
    state.error?.let { ErrorDialog(it, onDismiss = onBack) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticScaffold(nickname: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_diagnostic, nickname)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        FwknopIcon(R.drawable.ic_arrow_back_rounded, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) { content() }
    }
}

@Composable
fun DiagnosticContent(state: DiagnosticState, onEvent: (DiagnosticEvent) -> Unit) {
    val dimens = DimensTheme.dimens
    var key by rememberSaveable { mutableStateOf("") }
    var sourceIp by rememberSaveable { mutableStateOf("") }
    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
        modifier = Modifier
            .fillMaxWidth(dimens.contentWidthFraction)
            .verticalScroll(rememberScrollState())
            .padding(dimens.screenPadding),
    ) {
        Text(stringResource(R.string.diagnostic_intro), style = MaterialTheme.typography.bodyMedium)
        if (state.needsKey) {
            SecretTextField(key, { key = it }, stringResource(R.string.field_rijndael_key))
        }
        if (state.needsSourceIp) {
            FormTextField(
                value = sourceIp,
                onValueChange = { sourceIp = it },
                label = stringResource(R.string.field_source_ip_override),
                keyboardType = KeyboardType.Uri,
                supportingText = if (state.sourceIpInvalid) stringResource(R.string.error_source_ip_invalid) else null,
            )
        }
        DiagnosticButtons(state, onRun = { onEvent(DiagnosticEvent.Run(key, sourceIp)) }) {
            onEvent(DiagnosticEvent.Cancel)
        }
        SectionCard(stringResource(R.string.diagnostic_steps), R.drawable.ic_dns_rounded) {
            state.steps.forEach { StepRow(it) }
        }
        if (state.finished) DiagnosticVerdict(state.succeeded)
    }
}

@Composable
private fun DiagnosticButtons(state: DiagnosticState, onRun: () -> Unit, onCancel: () -> Unit) {
    if (state.running) {
        OutlinedButton(onClick = onCancel, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
    } else {
        val label = if (state.finished) R.string.diagnostic_rerun else R.string.diagnostic_run
        Button(onClick = onRun, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(label))
        }
    }
}

@Composable
private fun StepRow(step: DiagnosticStep) {
    val title = stringResource(step.phase.titleRes())
    val status = stringResource(step.status.labelRes())
    val detail = step.detail?.let { detailText(it) }
    val error = (step.status as? StepStatus.Failed)?.error?.text()
    val spoken = listOfNotNull(title, status, detail, error).joinToString(". ")
    Row(
        horizontalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { StatusIcon(step.status) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            val muted = step.status == StepStatus.Pending || step.status == StepStatus.Skipped
            Text(
                detail ?: status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun StatusIcon(status: StepStatus) {
    when (status) {
        StepStatus.Running -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        StepStatus.Done ->
            FwknopIcon(R.drawable.ic_check_rounded, null, tint = MaterialTheme.colorScheme.primary)
        is StepStatus.Failed ->
            FwknopIcon(R.drawable.ic_error_rounded, null, tint = MaterialTheme.colorScheme.error)
        StepStatus.Pending, StepStatus.Skipped -> Box(
            Modifier
                .size(12.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

@Composable
private fun DiagnosticVerdict(succeeded: Boolean) {
    val message = if (succeeded) R.string.diagnostic_success else R.string.diagnostic_failure
    Text(
        stringResource(message),
        style = MaterialTheme.typography.bodyLarge,
        color = if (succeeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun detailText(detail: DiagnosticDetail): String = when (detail) {
    is DiagnosticDetail.Server ->
        stringResource(R.string.diagnostic_server_detail, detail.host, detail.addresses.joinToString(", "))
    is DiagnosticDetail.Source -> detail.source.description()
    is DiagnosticDetail.Family -> stringResource(
        when (detail.family) {
            AddressFamily.IPV4 -> R.string.diagnostic_family_ipv4
            AddressFamily.IPV6 -> R.string.diagnostic_family_ipv6
        },
        detail.serverIp,
    )
    is DiagnosticDetail.Packet ->
        pluralStringResource(R.plurals.diagnostic_packet_size, detail.sizeBytes, detail.sizeBytes)
    is DiagnosticDetail.Transport -> transportText(detail.target)
}

@Composable
private fun transportText(target: KnockTarget): String =
    stringResource(R.string.diagnostic_transport_detail, target.protocol.name, target.address, target.port)

private fun DiagnosticPhase.titleRes(): Int = when (this) {
    DiagnosticPhase.SERVER_DNS -> R.string.diagnostic_step_server_dns
    DiagnosticPhase.SOURCE_IP -> R.string.diagnostic_step_source_ip
    DiagnosticPhase.ADDRESS_FAMILY -> R.string.diagnostic_step_family
    DiagnosticPhase.PACKET -> R.string.diagnostic_step_packet
    DiagnosticPhase.TRANSPORT -> R.string.diagnostic_step_transport
}

private fun StepStatus.labelRes(): Int = when (this) {
    StepStatus.Pending -> R.string.diagnostic_status_pending
    StepStatus.Running -> R.string.diagnostic_status_running
    StepStatus.Done -> R.string.diagnostic_status_done
    StepStatus.Skipped -> R.string.diagnostic_status_skipped
    is StepStatus.Failed -> R.string.diagnostic_status_failed
}

@AdaptivePreviews
@Composable
private fun DiagnosticContentPreview() {
    val steps = listOf(
        DiagnosticStep(
            DiagnosticPhase.SERVER_DNS,
            StepStatus.Done,
            DiagnosticDetail.Server("spa.example.org", listOf("2001:db8::10", "192.0.2.10")),
        ),
        DiagnosticStep(
            DiagnosticPhase.SOURCE_IP,
            StepStatus.Done,
            DiagnosticDetail.Source(ResolvedSourceIp("203.0.113.9", SourceIpMethod.Stun("stun.example.org"))),
        ),
        DiagnosticStep(
            DiagnosticPhase.ADDRESS_FAMILY,
            StepStatus.Done,
            DiagnosticDetail.Family(AddressFamily.IPV4, "192.0.2.10"),
        ),
        DiagnosticStep(DiagnosticPhase.PACKET, StepStatus.Done, DiagnosticDetail.Packet(184)),
        DiagnosticStep(
            DiagnosticPhase.TRANSPORT,
            StepStatus.Failed(UiError.Detailed(R.string.error_transport, "Network is unreachable")),
            DiagnosticDetail.Transport(KnockTarget("192.0.2.10", "spa.example.org", 62201, Protocol.UDP)),
        ),
    )
    FwknopTheme {
        DiagnosticContent(DiagnosticState(nickname = "home", loading = false, finished = true, steps = steps)) {}
    }
}
