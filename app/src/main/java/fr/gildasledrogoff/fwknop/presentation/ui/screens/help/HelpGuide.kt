package fr.gildasledrogoff.fwknop.presentation.ui.screens.help

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.FwknopIcon
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

private data class HelpTopic(
    @StringRes val title: Int,
    @StringRes val body: Int,
    @DrawableRes val icon: Int,
)

private val helpTopics = listOf(
    HelpTopic(R.string.help_topic_basics_title, R.string.help_topic_basics_body, R.drawable.ic_shield_rounded),
    HelpTopic(R.string.help_topic_config_title, R.string.help_topic_config_body, R.drawable.ic_key_rounded),
    HelpTopic(R.string.help_topic_source_ip_title, R.string.help_topic_source_ip_body, R.drawable.ic_dns_rounded),
    HelpTopic(R.string.help_topic_after_title, R.string.help_topic_after_body, R.drawable.ic_bolt_rounded),
    HelpTopic(R.string.help_topic_nfc_title, R.string.help_topic_nfc_body, R.drawable.ic_nfc_rounded),
    HelpTopic(R.string.help_topic_security_title, R.string.help_topic_security_body, R.drawable.ic_lock_rounded),
    HelpTopic(
        R.string.help_topic_troubleshooting_title,
        R.string.help_topic_troubleshooting_body,
        R.drawable.ic_error_rounded
    ),
)

private const val NONE = -1

@Composable
fun HelpGuide() {
    var expanded by rememberSaveable { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing)) {
        HelpSectionTitle(stringResource(R.string.help_section_guide))
        helpTopics.forEachIndexed { index, topic ->
            HelpTopicCard(
                topic = topic,
                expanded = expanded == index,
                onToggle = { expanded = if (expanded == index) NONE else index },
            )
        }
    }
}

@Composable
private fun HelpTopicCard(topic: HelpTopic, expanded: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dimens = DimensTheme.dimens
    val chevron by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    val container by animateColorAsState(
        if (expanded) colors.secondaryContainer else colors.surfaceContainerLow,
        label = "container",
    )
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.large,
        color = container,
        
        contentColor = if (expanded) colors.onSecondaryContainer else colors.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
            modifier = Modifier
                .animateContentSize()
                .padding(dimens.cardPadding),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
            ) {
                IconBadge(topic.icon)
                Text(
                    text = stringResource(topic.title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                FwknopIcon(
                    R.drawable.ic_chevron_right_rounded,
                    contentDescription = null,
                    modifier = Modifier.rotate(chevron),
                    tint = colors.onSurfaceVariant,
                )
            }
            if (expanded) {
                HtmlText(stringResource(topic.body), color = colors.onSecondaryContainer)
            }
        }
    }
}

@AdaptivePreviews
@Composable
private fun HelpGuidePreview() {
    FwknopTheme { HelpGuide() }
}
