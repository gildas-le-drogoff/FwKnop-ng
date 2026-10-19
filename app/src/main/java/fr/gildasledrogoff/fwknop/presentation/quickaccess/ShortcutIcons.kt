// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.quickaccess

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.IconCompat
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.ConfigIcon
import fr.gildasledrogoff.fwknop.presentation.ui.components.glyphRes
import kotlin.math.roundToInt

internal object ShortcutIcons {
    fun of(context: Context, icon: ConfigIcon): IconCompat {
        val glyph = icon.glyphRes() ?: return IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        return IconCompat.createWithAdaptiveBitmap(render(context, glyph))
    }

    private fun render(context: Context, @DrawableRes glyph: Int): Bitmap {
        val size = (ADAPTIVE_UNITS * context.resources.displayMetrics.density).roundToInt()
            .coerceAtMost(MAX_SIZE_PX)
        val scale = size / ADAPTIVE_UNITS
        fun px(units: Float) = (units * scale).roundToInt()
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        fun draw(@DrawableRes id: Int, left: Float, top: Float, side: Float, tint: Int? = null) {
            val drawable = ContextCompat.getDrawable(context, id)?.mutate() ?: return
            tint?.let { DrawableCompat.setTint(drawable, it) }
            drawable.setBounds(px(left), px(top), px(left + side), px(top + side))
            drawable.draw(canvas)
        }

        draw(R.drawable.ic_launcher_background, 0f, 0f, ADAPTIVE_UNITS)
        draw(glyph, GLYPH_START, GLYPH_START, GLYPH_SIDE, GLYPH_COLOR)
        return bitmap
    }

    private const val GLYPH_COLOR = 0xFFFFFFFF.toInt()
    private const val ADAPTIVE_UNITS = 108f
    private const val MAX_SIZE_PX = 512
    private const val GLYPH_SIDE = 44f
    private const val GLYPH_START = (ADAPTIVE_UNITS - GLYPH_SIDE) / 2
}
