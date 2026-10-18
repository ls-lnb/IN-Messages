package org.fossify.messages.activities

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.messages.R
import org.fossify.commons.extensions.toast
import org.fossify.messages.dialogs.AppColorPickerDialog
import org.fossify.messages.databinding.ActivityCustomizeColorsBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.appColorPalette
import org.fossify.messages.helpers.colorDistance
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
            runCatching {
                AppColorPickerDialog(
                    this,
                    currentIconVariant().color,
                    appIconVariants.map { it.color },
                    iconPreview = variantIconDrawable(),
                    showShades = false
                ) { wasPositive, color ->
                    if (wasPositive) {
                        applyIconVariant(closestIconVariant(color))
                    }
                }
            }.onFailure { toast("Icon picker failed: $it") }
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
        runCatching {
            AppColorPickerDialog(
                this,
                initialColor,
                appColorPalette,
                iconPreview = null,
                showShades = true
            ) { wasPositive, newColor ->
                if (wasPositive) {
                    apply(newColor)
                    updateColors()
                }
            }
        }.onFailure { toast("Colour picker failed: $it") }
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

    /** The launcher icon for [variant], used as the preview in the app icon picker. */
    private fun variantIconDrawable(variant: AppIconVariant = currentIconVariant()): Drawable? {
        val name = if (variant == defaultAppIconVariant) {
            "ic_launcher"
        } else {
            "ic_launcher_${variant.name.lowercase()}"
        }
        val iconRes = resources.getIdentifier(name, "mipmap", packageName)
        return if (iconRes == 0) null else getDrawable(iconRes)
    }
}
