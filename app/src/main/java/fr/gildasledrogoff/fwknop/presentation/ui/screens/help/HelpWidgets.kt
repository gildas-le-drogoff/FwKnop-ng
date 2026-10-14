// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.help

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fr.gildasledrogoff.fwknop.presentation.ui.components.FwknopIcon

@Composable
fun HelpSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

@Composable
fun IconBadge(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(container),
    ) {
        FwknopIcon(icon, contentDescription = null, tint = content)
    }
}

@Composable
fun AvatarBadge(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.medium),
    )
}

@Composable
fun HtmlText(html: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val links = rememberLinkStyles()
    val text =
        remember(html, links) { AnnotatedString.fromHtml(htmlString = html, linkStyles = links) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = modifier
    )
}

@Composable
private fun rememberLinkStyles(): TextLinkStyles {
    val color = MaterialTheme.colorScheme.primary
    return remember(color) {
        TextLinkStyles(
            SpanStyle(
                color = color,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.Underline
            )
        )
    }
}
