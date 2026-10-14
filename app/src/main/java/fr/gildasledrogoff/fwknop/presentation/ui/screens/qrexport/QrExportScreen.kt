// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.qrexport

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.BackupPassphrase
import fr.gildasledrogoff.fwknop.presentation.state.AuthPurpose
import fr.gildasledrogoff.fwknop.presentation.state.AuthResult
import fr.gildasledrogoff.fwknop.presentation.state.QrExportState
import fr.gildasledrogoff.fwknop.presentation.state.text
import fr.gildasledrogoff.fwknop.presentation.ui.components.*
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme
import fr.gildasledrogoff.fwknop.presentation.viewmodel.QrExportViewModel
import kotlinx.coroutines.launch

@Composable
fun QrExportScreen(onBack: () -> Unit, viewModel: QrExportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val savePng =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            if (uri != null) viewModel.savePng(uri)
        }
    val resources = LocalResources.current
    CollectEffects(viewModel.messages) { snackbarHostState.showSnackbar(resources.getString(it)) }
    QrExportAuthGate(state, viewModel::claimAuthPrompt, viewModel::onAuthResult, onBack)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    QrExportScaffold(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        actions = QrExportActions(
            onSavePng = { savePng.launch(viewModel.pngFileName()) },
            onShare = { scope.launch { viewModel.sharePng()?.let { sharePng(context, it) } } },
            onPage = viewModel::onPageChanged,
            onEncryptedChange = viewModel::onEncryptedChanged,
            onChangePassphrase = viewModel::onChangePassphrase,
        ),
    )
    if ((state as? QrExportState.Ready)?.askPassphrase == true) {
        PassphraseDialog(
            spec = PassphraseSpec(
                title = stringResource(R.string.qr_passphrase_title),
                hint = stringResource(R.string.hint_qr_passphrase, BackupPassphrase.MIN_LENGTH),
                confirmLabel = stringResource(R.string.action_save),
                creating = true,
            ),
            onSubmit = viewModel::onPassphraseEntered,
            onDismiss = viewModel::onPassphraseDismissed,
        )
    }
}

@Immutable
data class QrExportActions(
    val onSavePng: () -> Unit,
    val onShare: () -> Unit,
    val onPage: (delta: Int) -> Unit,
    val onEncryptedChange: (Boolean) -> Unit,
    val onChangePassphrase: () -> Unit,
)

@Composable
private fun QrExportAuthGate(
    state: QrExportState,
    claimPrompt: () -> Boolean,
    onResult: (AuthResult) -> Unit,
    onDenied: () -> Unit,
) {
    val authenticator = rememberUserAuthenticator(onResult)
    LaunchedEffect(state) {
        when {
            state == QrExportState.Denied -> onDenied()
            state == QrExportState.AwaitingAuth && claimPrompt() -> authenticator.launch(AuthPurpose.SHOW_QR)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrExportScaffold(
    state: QrExportState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    actions: QrExportActions,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_qr_export)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        FwknopIcon(
                            R.drawable.ic_arrow_back_rounded,
                            stringResource(R.string.action_back)
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding -> QrExportContent(state, actions, Modifier.padding(padding)) }
}

@Composable
fun QrExportContent(
    state: QrExportState,
    actions: QrExportActions,
    modifier: Modifier = Modifier,
) {
    when (state) {
        QrExportState.AwaitingAuth, QrExportState.Denied, QrExportState.Loading -> Box(modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        is QrExportState.Failed -> EmptyState(
            title = stringResource(R.string.error_title),
            body = state.error.text(),
            icon = R.drawable.ic_qr_code_rounded,
            modifier = modifier,
        )

        is QrExportState.Ready -> QrExportBody(state, actions, modifier)
    }
}

@Composable
private fun QrExportBody(
    state: QrExportState.Ready,
    actions: QrExportActions,
    modifier: Modifier = Modifier,
) {
    val dimens = DimensTheme.dimens
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(dimens.screenPadding),
    ) {
        QrImage(state.qr, state.qrDescription(), Modifier.fillMaxWidth(dimens.contentWidthFraction))
        if (state.pageCount > 1) PageSelector(state.page, state.pageCount, actions.onPage)
        Text(
            text = state.nickname,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = scaledMaxLines(1),
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        LabeledSwitch(
            label = stringResource(R.string.qr_export_encrypt),
            checked = state.encrypted,
            onCheckedChange = actions.onEncryptedChange,
            supportingText = stringResource(R.string.qr_export_encrypt_hint),
            modifier = Modifier.fillMaxWidth(dimens.contentWidthFraction),
        )
        Text(
            text = stringResource(
                if (state.encrypted) R.string.qr_export_encrypted_warning else R.string.qr_export_warning
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(dimens.contentWidthFraction),
        )
        ButtonRow(Modifier.fillMaxWidth(dimens.contentWidthFraction)) {
            OutlinedButton(actions.onSavePng) { Text(stringResource(R.string.action_save_png)) }
            Button(actions.onShare) { Text(stringResource(R.string.action_share_png)) }
        }
        if (state.encrypted) {
            TextButton(actions.onChangePassphrase) {
                Text(stringResource(R.string.action_change_qr_passphrase))
            }
        }
    }
}

@Composable
private fun PageSelector(page: Int, pageCount: Int, onPage: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPage(-1) }, enabled = page > 0) {
            FwknopIcon(
                R.drawable.ic_chevron_left_rounded,
                stringResource(R.string.action_previous_qr)
            )
        }

        val pageDescription = stringResource(R.string.a11y_qr_page, page + 1, pageCount)
        Text(
            text = stringResource(R.string.qr_export_page, page + 1, pageCount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics {
                contentDescription = pageDescription
                liveRegion = LiveRegionMode.Polite
            },
        )
        IconButton(onClick = { onPage(1) }, enabled = page < pageCount - 1) {
            FwknopIcon(R.drawable.ic_chevron_right_rounded, stringResource(R.string.action_next_qr))
        }
    }
}

@Composable
private fun QrImage(qr: ImageBitmap?, description: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White,
        contentColor = Color.Black,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        if (qr != null) {
            Image(
                bitmap = qr,
                contentDescription = description,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val generating = stringResource(R.string.a11y_qr_generating)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .semantics { contentDescription = generating })
        }
    }
}

@Composable
private fun QrExportState.Ready.qrDescription(): String = when {
    pageCount > 1 -> stringResource(R.string.a11y_qr_image_paged, nickname, page + 1, pageCount)
    else -> stringResource(R.string.a11y_qr_image, nickname)
}

private fun sharePng(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

@AdaptivePreviews
@Composable
private fun QrExportContentPreview() {
    FwknopTheme {
        QrExportContent(
            QrExportState.Ready("home", qr = null, page = 1, pageCount = 3, encrypted = true),
            QrExportActions({}, {}, {}, {}, {})
        )
    }
}

@LargeFontPreviews
@Composable
private fun QrExportLargeFontPreview() {
    FwknopTheme {
        QrExportContent(
            QrExportState.Ready(
                "home-office-nat",
                qr = null,
                page = 0,
                pageCount = 2,
                encrypted = true
            ),
            QrExportActions({}, {}, {}, {}, {})
        )
    }
}
