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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import kotlin.math.min

// Port of Android 10 ImageGradientColorizer, with the fade span made adjustable
// https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/packages/SystemUI/src/com/android/systemui/statusbar/notification/ImageGradientColorizer.java
object CoverColorizer {
    private const val LUMINANCE_RED = 0.2126f
    private const val LUMINANCE_GREEN = 0.7152f
    private const val LUMINANCE_BLUE = 0.0722f
    private const val TINT_ALPHA = 128
    private const val MASK_MIDPOINT = 0x80FFFFFF.toInt()
    private const val FIRST_STOP = 0.4f
    private const val SECOND_STOP = 0.6f

    // fade 是渐变占封面宽度的比例，超出部分被 CLAMP 成不透明，也就是保留原图
    // maxSize 按卡片高度裁剪，原图常有 1000px 以上，全尺寸跑三遍逐像素毫无意义
    fun colorize(artwork: Drawable, backgroundColor: Int, fade: Float, isRtl: Boolean, maxSize: Int): Bitmap? {
        val width = artwork.intrinsicWidth
        val height = artwork.intrinsicHeight
        if (width <= 0 || height <= 0 || maxSize <= 0) return null

        val size = min(min(width, height), maxSize)
        val scale = size / min(width, height).toFloat()
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()
        val widthInset = (scaledWidth - size) / 2
        val heightInset = (scaledHeight - size) / 2
        val cover = artwork.mutate()
        cover.setBounds(-widthInset, -heightInset, scaledWidth - widthInset, scaledHeight - heightInset)

        val red = Color.red(backgroundColor)
        val green = Color.green(backgroundColor)
        val blue = Color.blue(backgroundColor)
        val luminance =
            (red * LUMINANCE_RED + green * LUMINANCE_GREEN + blue * LUMINANCE_BLUE)
        val tint = ColorMatrix(
            floatArrayOf(
                LUMINANCE_RED, LUMINANCE_GREEN, LUMINANCE_BLUE, 0f, red - luminance,
                LUMINANCE_RED, LUMINANCE_GREEN, LUMINANCE_BLUE, 0f, green - luminance,
                LUMINANCE_RED, LUMINANCE_GREEN, LUMINANCE_BLUE, 0f, blue - luminance,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val faded = createBitmap(size, size)
        val fadedCanvas = Canvas(faded)
        cover.clearColorFilter()
        cover.draw(fadedCanvas)
        if (isRtl) {
            fadedCanvas.translate(size.toFloat(), 0f)
            fadedCanvas.scale(-1f, 1f)
        }

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        maskPaint.shader = fadeShader(size, FIRST_STOP * fade, fade)
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        fadedCanvas.drawPaint(maskPaint)

        val result = createBitmap(size, size)
        val canvas = Canvas(result)
        val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        tintPaint.colorFilter = ColorMatrixColorFilter(tint)
        tintPaint.alpha = TINT_ALPHA
        canvas.drawBitmap(faded, 0f, 0f, tintPaint)

        maskPaint.shader = fadeShader(size, SECOND_STOP * fade, fade)
        fadedCanvas.drawPaint(maskPaint)
        canvas.drawBitmap(faded, 0f, 0f, null)
        return result
    }

    private fun fadeShader(size: Int, midpoint: Float, end: Float) = LinearGradient(
        0f, 0f, size.toFloat(), 0f,
        intArrayOf(Color.TRANSPARENT, MASK_MIDPOINT, Color.BLACK),
        floatArrayOf(0f, midpoint, end),
        Shader.TileMode.CLAMP
    )
}
