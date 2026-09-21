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
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.palette.graphics.Palette
import kotlin.math.abs
import kotlin.math.sqrt

// Port of Android 10 MediaNotificationProcessor and Notification.Builder#ensureColors
// https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/packages/SystemUI/src/com/android/systemui/statusbar/notification/MediaNotificationProcessor.java
// https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/core/java/com/android/internal/util/ContrastColorUtil.java
object LegacyMediaColors {
    private const val POPULATION_FRACTION_FOR_MORE_VIBRANT = 1.0f
    private const val MIN_SATURATION_WHEN_DECIDING = 0.19f
    private const val MINIMUM_IMAGE_FRACTION = 0.002f
    private const val POPULATION_FRACTION_FOR_DOMINANT = 0.01f
    private const val POPULATION_FRACTION_FOR_WHITE_OR_BLACK = 2.5f
    private const val BLACK_MAX_LIGHTNESS = 0.08f
    private const val WHITE_MIN_LIGHTNESS = 0.90f
    private const val RESIZE_BITMAP_AREA = 150 * 150
    private const val TEXT_COLOR_START_WIDTH_FRACTION = 0.4f
    private const val LIGHTNESS_TEXT_DIFFERENCE_LIGHT = 20
    private const val LIGHTNESS_TEXT_DIFFERENCE_DARK = -10
    private const val MIN_CONTRAST = 4.5

    fun convertToColorConfig(artwork: Drawable): MediaViewColorConfig? {
        val bitmap = artwork.toPaletteBitmap() ?: return null
        val paletteBuilder = Palette.from(bitmap)
            .setRegion(0, 0, (bitmap.width / 2).coerceAtLeast(1), bitmap.height)
            .clearFilters()
            .resizeBitmapArea(RESIZE_BITMAP_AREA)
        val backgroundSwatch = findBackgroundSwatch(paletteBuilder.generate())
        val backgroundColor = backgroundSwatch.rgb

        paletteBuilder.setRegion(
            (bitmap.width * TEXT_COLOR_START_WIDTH_FRACTION).toInt(), 0,
            bitmap.width, bitmap.height
        )
        if (!isWhiteOrBlack(backgroundSwatch.hsl)) {
            val backgroundHue = backgroundSwatch.hsl[0]
            paletteBuilder.addFilter { _, hsl ->
                val diff = abs(hsl[0] - backgroundHue)
                diff > 10 && diff < 350
            }
        }
        paletteBuilder.addFilter { _, hsl -> !isWhiteOrBlack(hsl) }

        val foregroundColor = selectForegroundColor(backgroundColor, paletteBuilder.generate())
        val (textPrimary, textSecondary) = ensureTextColors(backgroundColor, foregroundColor)
        return MediaViewColorConfig(textPrimary, textSecondary, backgroundColor, backgroundColor)
    }

    private fun Drawable.toPaletteBitmap(): Bitmap? {
        var width = intrinsicWidth
        var height = intrinsicHeight
        if (width <= 0 || height <= 0) return null
        val area = width * height
        if (area > RESIZE_BITMAP_AREA) {
            val factor = sqrt(RESIZE_BITMAP_AREA.toDouble() / area)
            width = (factor * width).toInt().coerceAtLeast(1)
            height = (factor * height).toInt().coerceAtLeast(1)
        }
        val bitmap = createBitmap(width, height)
        setBounds(0, 0, width, height)
        draw(Canvas(bitmap))
        return bitmap
    }

    private fun findBackgroundSwatch(palette: Palette): Palette.Swatch {
        val dominantSwatch = palette.dominantSwatch ?: return Palette.Swatch(Color.WHITE, 100)
        if (!isWhiteOrBlack(dominantSwatch.hsl)) return dominantSwatch

        var highestNonWhitePopulation = -1f
        var second: Palette.Swatch? = null
        palette.swatches.forEach { swatch ->
            if (swatch !== dominantSwatch && swatch.population > highestNonWhitePopulation && !isWhiteOrBlack(swatch.hsl)) {
                second = swatch
                highestNonWhitePopulation = swatch.population.toFloat()
            }
        }
        val secondSwatch = second ?: return dominantSwatch
        return if (dominantSwatch.population / highestNonWhitePopulation > POPULATION_FRACTION_FOR_WHITE_OR_BLACK) {
            dominantSwatch
        } else {
            secondSwatch
        }
    }

    private fun selectForegroundColor(backgroundColor: Int, palette: Palette): Int {
        return if (isColorLight(backgroundColor)) {
            selectForegroundColorForSwatches(
                palette.darkVibrantSwatch, palette.vibrantSwatch,
                palette.darkMutedSwatch, palette.mutedSwatch,
                palette.dominantSwatch, Color.BLACK
            )
        } else {
            selectForegroundColorForSwatches(
                palette.lightVibrantSwatch, palette.vibrantSwatch,
                palette.lightMutedSwatch, palette.mutedSwatch,
                palette.dominantSwatch, Color.WHITE
            )
        }
    }

    private fun selectForegroundColorForSwatches(
        moreVibrant: Palette.Swatch?,
        vibrant: Palette.Swatch?,
        moreMutedSwatch: Palette.Swatch?,
        mutedSwatch: Palette.Swatch?,
        dominantSwatch: Palette.Swatch?,
        fallbackColor: Int
    ): Int {
        val coloredCandidate = selectVibrantCandidate(moreVibrant, vibrant)
            ?: selectMutedCandidate(mutedSwatch, moreMutedSwatch)
        if (coloredCandidate != null) {
            if (dominantSwatch == null || dominantSwatch === coloredCandidate) {
                return coloredCandidate.rgb
            }
            val fraction = coloredCandidate.population.toFloat() / dominantSwatch.population
            return if (fraction < POPULATION_FRACTION_FOR_DOMINANT && dominantSwatch.hsl[1] > MIN_SATURATION_WHEN_DECIDING) {
                dominantSwatch.rgb
            } else {
                coloredCandidate.rgb
            }
        }
        return if (hasEnoughPopulation(dominantSwatch)) dominantSwatch!!.rgb else fallbackColor
    }

    private fun selectMutedCandidate(first: Palette.Swatch?, second: Palette.Swatch?): Palette.Swatch? {
        val firstValid = hasEnoughPopulation(first)
        val secondValid = hasEnoughPopulation(second)
        if (firstValid && secondValid) {
            val populationFraction = first!!.population.toFloat() / second!!.population
            return if (first.hsl[1] * populationFraction > second.hsl[1]) first else second
        }
        return if (firstValid) first else if (secondValid) second else null
    }

    private fun selectVibrantCandidate(first: Palette.Swatch?, second: Palette.Swatch?): Palette.Swatch? {
        val firstValid = hasEnoughPopulation(first)
        val secondValid = hasEnoughPopulation(second)
        if (firstValid && secondValid) {
            val populationFraction = first!!.population.toFloat() / second!!.population
            return if (populationFraction < POPULATION_FRACTION_FOR_MORE_VIBRANT) second else first
        }
        return if (firstValid) first else if (secondValid) second else null
    }

    private fun hasEnoughPopulation(swatch: Palette.Swatch?): Boolean {
        return swatch != null && swatch.population / RESIZE_BITMAP_AREA.toFloat() > MINIMUM_IMAGE_FRACTION
    }

    private fun ensureTextColors(backgroundColor: Int, foregroundColor: Int): Pair<Int, Int> {
        val backLum = ColorUtils.calculateLuminance(backgroundColor)
        val textLum = ColorUtils.calculateLuminance(foregroundColor)
        val backgroundLight =
            backLum > textLum && satisfiesTextContrast(backgroundColor, Color.BLACK) ||
                backLum <= textLum && !satisfiesTextContrast(backgroundColor, Color.WHITE)
        val lightnessDifference =
            if (backgroundLight) LIGHTNESS_TEXT_DIFFERENCE_LIGHT else LIGHTNESS_TEXT_DIFFERENCE_DARK

        if (contrast(foregroundColor, backgroundColor) < MIN_CONTRAST) {
            val secondary = contrastingColor(foregroundColor, backgroundColor, backgroundLight)
            return changeColorLightness(secondary, -lightnessDifference) to secondary
        }

        val secondary = changeColorLightness(foregroundColor, lightnessDifference)
        if (contrast(secondary, backgroundColor) >= MIN_CONTRAST) {
            return foregroundColor to secondary
        }
        val fixedSecondary = contrastingColor(secondary, backgroundColor, backgroundLight)
        return changeColorLightness(fixedSecondary, -lightnessDifference) to fixedSecondary
    }

    private fun contrastingColor(color: Int, backgroundColor: Int, backgroundLight: Boolean): Int {
        return if (backgroundLight) {
            findContrastColor(color, backgroundColor)
        } else {
            findContrastColorAgainstDark(color, backgroundColor)
        }
    }

    private fun findContrastColor(color: Int, backgroundColor: Int): Int {
        if (contrast(color, backgroundColor) >= MIN_CONTRAST) return color
        val lab = DoubleArray(3)
        ColorUtils.colorToLAB(color, lab)
        val a = lab[1]
        val b = lab[2]
        var low = 0.0
        var high = lab[0]
        var i = 0
        while (i < 15 && high - low > 0.00001) {
            val l = (low + high) / 2
            if (contrast(ColorUtils.LABToColor(l, a, b), backgroundColor) > MIN_CONTRAST) {
                low = l
            } else {
                high = l
            }
            i++
        }
        return ColorUtils.LABToColor(low, a, b)
    }

    private fun findContrastColorAgainstDark(color: Int, backgroundColor: Int): Int {
        if (contrast(color, backgroundColor) >= MIN_CONTRAST) return color
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        var result = color
        var low = hsl[2]
        var high = 1f
        var i = 0
        while (i < 15 && high - low > 0.00001f) {
            val l = (low + high) / 2
            hsl[2] = l
            result = ColorUtils.HSLToColor(hsl)
            if (contrast(result, backgroundColor) > MIN_CONTRAST) {
                high = l
            } else {
                low = l
            }
            i++
        }
        return result
    }

    private fun changeColorLightness(baseColor: Int, amount: Int): Int {
        val lab = DoubleArray(3)
        ColorUtils.colorToLAB(baseColor, lab)
        return ColorUtils.LABToColor((lab[0] + amount).coerceIn(0.0, 100.0), lab[1], lab[2])
    }

    private fun contrast(foregroundColor: Int, backgroundColor: Int): Double {
        return ColorUtils.calculateContrast(foregroundColor, opaque(backgroundColor))
    }

    private fun satisfiesTextContrast(backgroundColor: Int, foregroundColor: Int): Boolean {
        return contrast(foregroundColor, backgroundColor) >= MIN_CONTRAST
    }

    private fun isColorLight(backgroundColor: Int): Boolean {
        return ColorUtils.calculateLuminance(backgroundColor) > 0.5
    }

    private fun opaque(color: Int) = color or (0xFF shl 24)

    private fun isWhiteOrBlack(hsl: FloatArray): Boolean {
        return hsl[2] <= BLACK_MAX_LIGHTNESS || hsl[2] >= WHITE_MIN_LIGHTNESS
    }
}
