package org.fossify.messages.activities

import android.os.Bundle
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.helpers.isSPlus
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.messages.R
import org.fossify.commons.extensions.toast
import org.fossify.messages.dialogs.AppColorPickerDialog
import org.fossify.messages.databinding.ActivityCustomizeColorsBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.appColorPalette
import org.fossify.messages.helpers.AppIconSwitcher
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
 * change. That switching lives in AppIconSwitcher.
 *
 * A colour of 0 means "use the theme default", which is what the reset action writes.
 *
 * On Android 12+ commons resolves every colour through the system theme first, so a colour picked
 * while that is on would be stored and then ignored. The switch on this screen turns it off, and
 * picking a colour does the same, so a colour never appears to do nothing.
 */
class CustomizeColorsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityCustomizeColorsBinding::inflate)
    private val iconSwitcher by lazy { AppIconSwitcher(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.colorsNestedScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.colorsNestedScrollview,
            topAppBar = binding.colorsAppbar
        )
        setupOptionsMenu()
        setupSystemThemeRow()

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
                    iconSwitcher.current().color,
                    appIconVariants.map { it.color },
                    iconPreview = iconSwitcher.iconDrawable(),
                    showShades = false
                ) { wasPositive, color ->
                    if (wasPositive) {
                        iconSwitcher.apply(iconSwitcher.closest(color))
                        updateColors()
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
                iconSwitcher.apply(defaultAppIconVariant)
                updateColors()
                true
            } else {
                false
            }
        }
    }

    private fun setupSystemThemeRow() {
        binding.colorsSystemThemeHolder.beVisibleIf(isSPlus())
        binding.colorsSystemTheme.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != config.isSystemThemeEnabled) {
                config.isSystemThemeEnabled = isChecked
                recreate()
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
                    stopFollowingSystemColors()
                    updateColors()
                }
            }
        }.onFailure { toast("Colour picker failed: $it") }
    }

    /**
     * A picked colour cannot take effect while the system theme owns the colours on Android 12+,
     * so following it is turned off here rather than leaving the pick looking broken.
     */
    private fun stopFollowingSystemColors() {
        if (!isSPlus() || !config.isSystemThemeEnabled) {
            return
        }

        config.isSystemThemeEnabled = false
        toast(R.string.stopped_following_system_colors)
        recreate()
    }

    private fun updateColors() {
        updateTextColors(binding.colorsHolder)
        binding.colorsSystemTheme.isChecked = config.isSystemThemeEnabled
        binding.colorsSystemThemeHint.beVisibleIf(config.isSystemThemeEnabled)
        binding.colorsPrimarySwatch.setColorFilter(getProperPrimaryColor())
        binding.colorsTextSwatch.setColorFilter(getProperTextColor())
        binding.colorsBackgroundSwatch.setColorFilter(getProperBackgroundColor())
        binding.colorsIconSwatch.setColorFilter(iconSwitcher.current().color)
    }
}
