package org.fossify.messages.activities

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import org.fossify.commons.dialogs.LineColorPickerDialog
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityCustomizeColorsBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.AppIconVariant
import org.fossify.messages.helpers.appIconVariants
import org.fossify.messages.helpers.defaultAppIconVariant

/**
 * Colour customization owned by this fork. Commons only offers these rows when its Thank You app
 * is installed, because it saves them through a shared settings provider - a fork cannot have
 * that, so the colours go into this app's own preferences instead. Commons applies them through
 * getProperPrimaryColor(), getProperTextColor() and getProperBackgroundColor() everywhere.
 *
 * The app icon is different: it is one of the launcher aliases declared in the manifest, so
 * choosing one enables that alias and disables the rest, which is what makes the launcher icon
 * change. Commons' own helper for this reads R.array.app_icon_colors, an array this app never
 * defined, so the switching is done here.
 *
 * A colour of 0 means "use the theme default", which is what the reset action writes.
 */
class CustomizeColorsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityCustomizeColorsBinding::inflate)

    /** The launcher palette, which doubles as the base strip for the general colours. */
    private val palette = ArrayList(appIconVariants.map { it.color })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.colorsNestedScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.colorsNestedScrollview,
            topAppBar = binding.colorsAppbar
        )
        setupOptionsMenu()

        binding.colorsPrimaryHolder.setOnClickListener {
            pickColor(config.primaryColor, getProperPrimaryColor()) { config.primaryColor = it }
        }
        binding.colorsTextHolder.setOnClickListener {
            pickColor(config.textColor, getProperTextColor()) { config.textColor = it }
        }
        binding.colorsBackgroundHolder.setOnClickListener {
            pickColor(config.backgroundColor, getProperBackgroundColor()) { config.backgroundColor = it }
        }
        binding.colorsIconHolder.setOnClickListener {
            // one strip and no shades: only the launcher's own colours are valid
            LineColorPickerDialog(
                this,
                currentIconVariant().color,
                false,
                ICON_PICKER_LINES,
                palette,
                binding.colorsAppbar
            ) { wasPositive, color ->
                if (wasPositive) {
                    applyIconVariant(closestIconVariant(color))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.colorsAppbar, NavigationIcon.Arrow, topBarColor = getProperBackgroundColor())
        updateColors()
    }

    private fun setupOptionsMenu() {
        binding.colorsToolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.reset_colors) {
                config.primaryColor = 0
                config.textColor = 0
                config.backgroundColor = 0
                applyIconVariant(defaultAppIconVariant)
                updateColors()
                true
            } else {
                false
            }
        }
    }

    /** [current] is 0 while the default is in use, in which case the dialog opens on [shown]. */
    private fun pickColor(current: Int, shown: Int, apply: (Int) -> Unit) {
        val initialColor = if (current == 0) shown else current
        LineColorPickerDialog(
            this,
            initialColor,
            false,
            COLOR_PICKER_LINES,
            palette,
            binding.colorsAppbar
        ) { wasPositive, newColor ->
            if (wasPositive) {
                apply(newColor)
                updateColors()
            }
        }
    }

    private fun updateColors() {
        updateTextColors(binding.colorsHolder)
        binding.colorsPrimarySwatch.setColorFilter(getProperPrimaryColor())
        binding.colorsTextSwatch.setColorFilter(getProperTextColor())
        binding.colorsBackgroundSwatch.setColorFilter(getProperBackgroundColor())
        binding.colorsIconSwatch.setColorFilter(currentIconVariant().color)
    }

    /** The alias the launcher is currently using; falls back to the shipped one. */
    private fun currentIconVariant(): AppIconVariant {
        val manager = packageManager
        return appIconVariants.firstOrNull { variant ->
            manager.getComponentEnabledSetting(iconComponent(variant)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: defaultAppIconVariant
    }

    private fun closestIconVariant(color: Int): AppIconVariant {
        return appIconVariants.firstOrNull { it.color == color }
            ?: appIconVariants.minByOrNull { colorDistance(it.color, color) }
            ?: defaultAppIconVariant
    }

    private fun colorDistance(first: Int, second: Int): Int {
        val red = Color.red(first) - Color.red(second)
        val green = Color.green(first) - Color.green(second)
        val blue = Color.blue(first) - Color.blue(second)
        return red * red + green * green + blue * blue
    }

    /** Enables the chosen alias and disables the rest, which is what changes the launcher icon. */
    private fun applyIconVariant(variant: AppIconVariant) {
        val manager = packageManager
        appIconVariants.forEach { other ->
            val state = if (other == variant) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            manager.setComponentEnabledSetting(iconComponent(other), state, PackageManager.DONT_KILL_APP)
        }

        config.appIconColor = variant.color
        updateColors()
    }

    private fun iconComponent(variant: AppIconVariant): ComponentName {
        return ComponentName(packageName, "$packageName.activities.SplashActivity.${variant.name}")
    }

    companion object {
        /** one strip for the launcher colours, a strip plus shades for the free ones */
        private const val ICON_PICKER_LINES = 1
        private const val COLOR_PICKER_LINES = 2
    }
}
