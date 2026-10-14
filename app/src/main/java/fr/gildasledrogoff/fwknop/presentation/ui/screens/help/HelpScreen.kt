// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.help

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import fr.gildasledrogoff.fwknop.BuildConfig
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.FwknopIcon
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme
import fr.gildasledrogoff.fwknop.presentation.viewmodel.HelpViewModel

private const val LOGO_FOREGROUND_SCALE = 108f / 72f

@Suppress("UnusedParameter")
@Composable
fun HelpScreen(onBack: () -> Unit, viewModel: HelpViewModel = hiltViewModel()) {
    HelpScaffold(onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpScaffold(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_help)) },
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
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding), contentAlignment = Alignment.TopCenter
        ) {
            HelpContent()
        }
    }
}

@Composable
fun HelpContent() {
    val dimens = DimensTheme.dimens
    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
        modifier = Modifier
            .fillMaxWidth(dimens.contentWidthFraction)
            .verticalScroll(rememberScrollState())
            .padding(dimens.screenPadding),
    ) {
        HelpHero()
        HelpGuide()
        CreditsSection()
    }
}

@Composable
private fun HelpHero() {
    val colors = MaterialTheme.colorScheme
    val dimens = DimensTheme.dimens
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer))
            )
            .padding(horizontal = dimens.cardPadding, vertical = dimens.sectionSpacing * 1.5f),
    ) {
        AppLogo(Modifier.size(dimens.heroIcon))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onPrimaryContainer,
        )
        Text(
            text = stringResource(R.string.help_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onPrimaryContainer,
            textAlign = TextAlign.Center,
        )
        Surface(
            shape = CircleShape,
            color = colors.surface.copy(alpha = 0.7f),
            contentColor = colors.onSurface,
        ) {
            Text(
                text = stringResource(R.string.help_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    Box(
        modifier
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
    ) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.matchParentSize())
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .matchParentSize()
                .scale(LOGO_FOREGROUND_SCALE),
        )
    }
}

@AdaptivePreviews
@Composable
private fun HelpContentPreview() {
    FwknopTheme { HelpContent() }
}
