/*
 * This file is part of HyperCeiler.

 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2023-2025 HyperCeiler Contributions
 */
package com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.mediabackground

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LayoutDirection
import androidx.core.graphics.drawable.toDrawable
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.MediaControlBgFactory.toSquare
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.drawable.LinearGradientDrawable
import com.sevtinge.hyperceiler.hook.module.hook.systemui.base.controlcenter.drawable.MediaControlBgDrawable
import com.sevtinge.hyperceiler.hook.utils.prefs.PrefsUtils
import kotlin.math.min

// 与线性渐变布局一致，但颜色取自 Android 10 的媒体通知算法，而非 Monet 色板
class AndroidTenProcessor : BgProcessor {
    private val useAnim = PrefsUtils.mPrefsMap.getBoolean("system_ui_control_center_media_control_control_color_anim")
    private val coverFade = PrefsUtils.mPrefsMap.getInt("system_ui_control_center_media_control_cover_fade", 0).coerceIn(0, 100)

    override fun convertToColorConfig(
        artwork: Drawable,
        neutral1: List<Int>,
        neutral2: List<Int>,
        accent1: List<Int>,
        accent2: List<Int>
    ): MediaViewColorConfig {
        return LegacyMediaColors.convertToColorConfig(artwork)
            ?: MediaViewColorConfig(accent1[2], accent1[2], accent1[8], accent1[8])
    }

    override fun processAlbumCover(
        artwork: Drawable,
        colorConfig: MediaViewColorConfig,
        context: Context,
        width: Int,
        height: Int
    ): Drawable {
        if (coverFade > 0) {
            val isRtl = context.resources.configuration.layoutDirection == LayoutDirection.RTL
            CoverColorizer.colorize(artwork, colorConfig.bgStartColor, coverFade / 100f, isRtl, min(width, height))
                ?.let { return it.toDrawable(context.resources) }
        }
        return artwork.toSquare(context.resources, true, colorConfig.bgStartColor)
    }

    override fun textEndInset(width: Int, height: Int): Int =
        (min(width, height) * ARTWORK_TEXT_OVERLAP).toInt()

    override fun createBackground(
        artwork: Drawable,
        colorConfig: MediaViewColorConfig
    ): MediaControlBgDrawable {
        return LinearGradientDrawable(
            artwork,
            colorConfig,
            useAnim,
            drawGradient = coverFade == 0
        )
    }

    private companion object {
        // 渐变左缘仍是纯背景色，文本可以压进封面宽度的三成
        const val ARTWORK_TEXT_OVERLAP = 0.7f
    }
}
