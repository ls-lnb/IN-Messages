package org.fossify.messages.activities

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.RadioItem
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
 * A colour of 0 means "use the theme default", which is also what the reset action writes.
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
            val variants = appIconVariants
            val items = ArrayList<RadioItem>(variants.size)
            variants.forEachIndexed { index, variant ->
                items.add(RadioItem(index, variant.name.replace('_', ' ')))
            }

            RadioGroupDialog(this, items, variants.indexOf(currentIconVariant())) { selectedId ->
                applyIconVariant(variants[selectedId as Int])
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
        ColorPickerDialog(
            this,
            if (current == 0) shown else current,
            false,
            true,
            {},
            { wasPositive, newColor ->
                if (wasPositive) {
                    apply(newColor)
                    updateColors()
                }
            }
        )
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
        val packageManager = packageManager
        return appIconVariants.firstOrNull { variant ->
            val state = packageManager.getComponentEnabledSetting(iconComponent(variant))
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: defaultAppIconVariant
    }

    /** Enables the chosen alias and disables the rest, which is what changes the launcher icon. */
    private fun applyIconVariant(variant: AppIconVariant) {
        val packageManager = packageManager
        appIconVariants.forEach { other ->
            val state = if (other == variant) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            packageManager.setComponentEnabledSetting(
                iconComponent(other),
                state,
                PackageManager.DONT_KILL_APP
            )
        }

        config.appIconColor = variant.color
        updateColors()
    }

    private fun iconComponent(variant: AppIconVariant): ComponentName {
        return ComponentName(packageName, "$packageName.activities.SplashActivity.${variant.name}")
    }
}
