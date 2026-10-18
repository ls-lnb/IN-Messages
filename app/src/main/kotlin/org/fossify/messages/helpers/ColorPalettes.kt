package org.fossify.messages.helpers

import android.graphics.Color

/**
 * The colours offered by the in-app picker: the launcher palette, plus the greys, white and
 * black that the text and background rows need. The first 19 are the same colours as the app
 * icon variants, so both pickers share one vocabulary.
 */
val appColorPalette: List<Int> = appIconVariants.map { it.color } + listOf(
    0xFF9E9E9E.toInt(),
    0xFFBDBDBD.toInt(),
    0xFF757575.toInt(),
    0xFFFFFFFF.toInt(),
    0xFF000000.toInt(),
)

/** [count] shades of [color], lightest first, ending with the colour itself. */
fun colorShades(color: Int, count: Int): List<Int> {
    if (count <= 1) {
        return listOf(color)
    }

    return (1..count).map { step -> blend(Color.WHITE, color, step / count.toFloat()) }
}

/** A mix of [from] and [to], where a ratio of 0 is [from] and 1 is [to]. */
private fun blend(from: Int, to: Int, ratio: Float): Int {
    val red = (Color.red(from) + (Color.red(to) - Color.red(from)) * ratio).toInt()
    val green = (Color.green(from) + (Color.green(to) - Color.green(from)) * ratio).toInt()
    val blue = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * ratio).toInt()
    return Color.rgb(red, green, blue)
}

/** The squared RGB distance between two colours, used to match a picked colour to the palette. */
fun colorDistance(first: Int, second: Int): Int {
    val red = Color.red(first) - Color.red(second)
    val green = Color.green(first) - Color.green(second)
    val blue = Color.blue(first) - Color.blue(second)
    return red * red + green * green + blue * blue
}

/** True when [color] is bright enough that dark text belongs on it. */
fun isLightColor(color: Int): Boolean {
    return Color.luminance(color) > LIGHT_COLOR_LUMINANCE
}

/** Rough contrast ratio between two colours, used to notice a pair that would be hard to read. */
fun contrastRatio(first: Int, second: Int): Double {
    val lighter = maxOf(Color.luminance(first), Color.luminance(second))
    val darker = minOf(Color.luminance(first), Color.luminance(second))
    return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
}

private const val LIGHT_COLOR_LUMINANCE = 0.5
private const val CONTRAST_OFFSET = 0.05
