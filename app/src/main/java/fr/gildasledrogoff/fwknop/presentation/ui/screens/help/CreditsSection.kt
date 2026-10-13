package fr.gildasledrogoff.fwknop.presentation.ui.screens.help

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.FwknopIcon
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

private const val REPO_URL = "https://github.com/gildas-le-drogoff/FwKnop-ng"

private data class Credit(
    @StringRes val title: Int,
    @StringRes val body: Int,
    @DrawableRes val icon: Int,
)

private val credits = listOf(
    Credit(R.string.credits_authors_title, R.string.credits_authors_body, R.drawable.ic_group_rounded),
    Credit(R.string.credits_contributors_title, R.string.credits_contributors_body, R.drawable.ic_code_rounded),
    Credit(R.string.credits_artwork_title, R.string.credits_artwork_body, R.drawable.ic_palette_rounded),
)

@Composable
fun CreditsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing)) {
        HelpSectionTitle(stringResource(R.string.section_credits))
        LicenseCard()
        PeopleCard()
        LibrariesCard()
    }
}

@Composable
private fun LicenseCard() {
    val colors = MaterialTheme.colorScheme
    val dimens = DimensTheme.dimens
    val uriHandler = LocalUriHandler.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
            modifier = Modifier.padding(dimens.cardPadding),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
            ) {
                IconBadge(
                    R.drawable.ic_gavel_rounded,
                    container = colors.tertiary,
                    content = colors.onTertiary,
                )
                Text(
                    stringResource(R.string.credits_license_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            HtmlText(stringResource(R.string.credits_license_body))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { uriHandler.openUri(REPO_URL) }) {
                    ButtonIcon(R.drawable.ic_code_rounded)
                    Text(stringResource(R.string.credits_action_source))
                }
                OutlinedButton(onClick = { uriHandler.openUri("$REPO_URL/issues") }) {
                    ButtonIcon(R.drawable.ic_open_in_new_rounded)
                    Text(stringResource(R.string.credits_action_issues))
                }
            }
        }
    }
}

@Composable
private fun ButtonIcon(@DrawableRes icon: Int) {
    FwknopIcon(
        icon,
        contentDescription = null,
        modifier = Modifier
            .padding(end = ButtonDefaults.IconSpacing)
            .size(ButtonDefaults.IconSize),
    )
}

@Composable
private fun PeopleCard() {
    val dimens = DimensTheme.dimens
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
            modifier = Modifier.padding(dimens.cardPadding),
        ) {
            credits.forEach { CreditRow(it) }
        }
    }
}

@Composable
private fun CreditRow(credit: Credit) {
    Row(horizontalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing)) {
        IconBadge(
            credit.icon,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(credit.title), style = MaterialTheme.typography.titleSmall)
            HtmlText(
                stringResource(credit.body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibrariesCard() {
    val dimens = DimensTheme.dimens
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
                modifier = Modifier.padding(dimens.cardPadding),
            ) {
                IconBadge(R.drawable.ic_menu_book_rounded)
                Text(
                    stringResource(R.string.credits_libraries_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            HtmlText(
                stringResource(R.string.credits_libraries_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = dimens.cardPadding),
            )
            Column(Modifier.padding(vertical = dimens.itemSpacing)) {
                openSourceLibraries.forEachIndexed { index, library ->
                    if (index > 0) {
                        HorizontalDivider(
                            Modifier.padding(horizontal = dimens.cardPadding),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    LibraryItem(library)
                }
            }
        }
    }
}

@Composable
private fun LibraryItem(library: OpenSourceLibrary) {
    val colors = MaterialTheme.colorScheme
    val dimens = DimensTheme.dimens
    val uriHandler = LocalUriHandler.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { uriHandler.openUri(library.url) }
            .padding(horizontal = dimens.cardPadding, vertical = dimens.itemSpacing),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(library.name, style = MaterialTheme.typography.titleSmall)
            LicenseBadge(library.license)
            library.notice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        FwknopIcon(
            R.drawable.ic_open_in_new_rounded,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun LicenseBadge(license: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            license,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@AdaptivePreviews
@Composable
private fun CreditsSectionPreview() {
    FwknopTheme { CreditsSection() }
}
