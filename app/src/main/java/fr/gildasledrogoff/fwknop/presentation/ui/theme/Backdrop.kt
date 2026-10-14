// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.networkBackdrop(glow: Boolean = false): Modifier {
    val colors = MaterialTheme.colorScheme
    val surface = colors.surface
    val ink = colors.primary
    val glowColor = colors.primaryContainer.copy(
        alpha = if (LocalDarkTheme.current) 0.25f else 0.35f
    )
    return drawWithCache {
        val step = CELL.toPx()
        val cols = (size.width / step).toInt() + 2
        val rows = (size.height / step).toInt() + 2
        val nodes = Array(rows) { r -> Array(cols) { c -> node(r, c, step) } }
        val links = Path()
        for (r in 0 until rows) for (c in 0 until cols) {
            val bits = mix(r, c)
            if (c + 1 < cols && (bits and 7) == 0) links.line(nodes[r][c], nodes[r][c + 1])
            if (r + 1 < rows && (bits and 56) == 0) links.line(nodes[r][c], nodes[r + 1][c])
            if (r + 1 < rows && c + 1 < cols && (bits and 448) == 0) links.line(
                nodes[r][c],
                nodes[r + 1][c + 1]
            )
        }
        val stroke = Stroke(width = 1.dp.toPx())
        val dot = 1.2.dp.toPx()
        val hub = 2.dp.toPx()
        val glowBrush =
            Brush.verticalGradient(listOf(glowColor, Color.Transparent), endY = GLOW.toPx())
        onDrawBehind {
            drawRect(surface)
            if (glow) drawRect(glowBrush)
            drawPath(links, ink.copy(alpha = 0.03f), style = stroke)
            for (r in 0 until rows) for (c in 0 until cols) {
                val hubNode = mix(c, r) % 12 == 0
                drawCircle(
                    ink.copy(alpha = if (hubNode) 0.06f else 0.04f),
                    radius = if (hubNode) hub else dot,
                    center = nodes[r][c],
                )
            }
        }
    }
}

private val CELL: Dp = 96.dp
private val GLOW: Dp = 320.dp
private fun node(row: Int, col: Int, step: Float): Offset {
    val bits = mix(row * 31 + 7, col * 17 + 3)
    val dx = ((bits and 0xFF) / 255f - 0.5f) * step * 0.6f
    val dy = (((bits shr 8) and 0xFF) / 255f - 0.5f) * step * 0.6f
    return Offset(col * step + dx, row * step + dy)
}

private fun mix(a: Int, b: Int): Int {
    var h = a * 0x27D4EB2D xor b * 0x165667B1
    h = h xor (h ushr 15)
    h *= 0x2C1B3C6D
    return (h xor (h ushr 13)) and Int.MAX_VALUE
}

private fun Path.line(from: Offset, to: Offset) {
    moveTo(from.x, from.y)
    lineTo(to.x, to.y)
}
