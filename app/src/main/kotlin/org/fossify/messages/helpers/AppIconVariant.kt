package org.fossify.messages.helpers

/**
 * The launcher icon colours this app ships, matching the `.activities.SplashActivity.<Name>`
 * activity-alias entries in the manifest. Selecting a variant enables its alias and disables the
 * rest, which is what makes the launcher icon change.
 *
 * The colours are the Material 700 values those icons are drawn with, so the swatches, the
 * shortcut tint and the notification accent all match the icon itself rather than a lighter
 * shade of it. They are kept in `config.appIconColor` as well.
 */
data class AppIconVariant(val name: String, val color: Int)

val appIconVariants = listOf(
    AppIconVariant("Red", 0xD32F2FFF.toInt()),
    AppIconVariant("Pink", 0xC2185BFF.toInt()),
    AppIconVariant("Purple", 0x7B1FA2FF.toInt()),
    AppIconVariant("Deep_purple", 0x512DA8FF.toInt()),
    AppIconVariant("Indigo", 0x303F9FFF.toInt()),
    AppIconVariant("Blue", 0x2196F3FF.toInt()),
    AppIconVariant("Light_blue", 0x0288D1FF.toInt()),
    AppIconVariant("Cyan", 0x0097A7FF.toInt()),
    AppIconVariant("Teal", 0x00796BFF.toInt()),
    AppIconVariant("Green", 0x388E3CFF.toInt()),
    AppIconVariant("Light_green", 0x689F38FF.toInt()),
    AppIconVariant("Lime", 0xAFB42BFF.toInt()),
    AppIconVariant("Yellow", 0xFBC02DFF.toInt()),
    AppIconVariant("Amber", 0xFFA000FF.toInt()),
    AppIconVariant("Orange", 0xF57C00FF.toInt()),
    AppIconVariant("Deep_orange", 0xE64A19FF.toInt()),
    AppIconVariant("Brown", 0x5D4037FF.toInt()),
    AppIconVariant("Blue_grey", 0x455A64FF.toInt()),
    AppIconVariant("Grey_black", 0x212121FF.toInt()),
)

/** The variant a fresh install uses, matching the enabled alias in the manifest. */
val defaultAppIconVariant = appIconVariants.first { it.name == "Green" }
