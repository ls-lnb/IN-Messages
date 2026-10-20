package org.fossify.messages.helpers

/**
 * The launcher icon colours this app ships, matching the `.activities.SplashActivity.<Name>`
 * activity-alias entries in the manifest. Selecting a variant enables its alias and disables the
 * rest, which is what makes the launcher icon change.
 *
 * The colours are the Material palette values those icons are drawn with; they are kept in
 * `config.appIconColor` as well, which tints the "new contact" shortcut.
 */
data class AppIconVariant(val name: String, val color: Int)

val appIconVariants = listOf(
    AppIconVariant("Red", 0xFFF44336.toInt()),
    AppIconVariant("Pink", 0xFFE91E63.toInt()),
    AppIconVariant("Purple", 0xFF9C27B0.toInt()),
    AppIconVariant("Deep_purple", 0xFF673AB7.toInt()),
    AppIconVariant("Indigo", 0xFF3F51B5.toInt()),
    AppIconVariant("Blue", 0xFF2196F3.toInt()),
    AppIconVariant("Light_blue", 0xFF03A9F4.toInt()),
    AppIconVariant("Cyan", 0xFF00BCD4.toInt()),
    AppIconVariant("Teal", 0xFF009688.toInt()),
    AppIconVariant("Green", 0xFF4CAF50.toInt()),
    AppIconVariant("Light_green", 0xFF8BC34A.toInt()),
    AppIconVariant("Lime", 0xFFCDDC39.toInt()),
    AppIconVariant("Yellow", 0xFFFFEB3B.toInt()),
    AppIconVariant("Amber", 0xFFFFC107.toInt()),
    AppIconVariant("Orange", 0xFFFF9800.toInt()),
    AppIconVariant("Deep_orange", 0xFFFF5722.toInt()),
    AppIconVariant("Brown", 0xFF795548.toInt()),
    AppIconVariant("Blue_grey", 0xFF607D8B.toInt()),
    AppIconVariant("Grey_black", 0xFF212121.toInt()),
)

/** The variant a fresh install uses, matching the icon @mipmap/ic_launcher draws. */
val defaultAppIconVariant = appIconVariants.first { it.name == "Blue" }
