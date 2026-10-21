package org.fossify.messages.activities

import android.os.Bundle
import androidx.core.content.ContextCompat
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
import org.fossify.messages.helpers.contrastRatio
import org.fossify.messages.helpers.isLightColor

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
 * On Android 12+ commons resolves every colour through the system theme first, so while the app
 * follows the system theme these colours are ignored. Rather than accept a colour that would do
 * nothing, the colour rows and the reset action are disabled in that state; the switch turns
 * following off, which is what allows picking.
 *
 * A colour of 0 is not "use the theme default" either: commons draws a stored 0 verbatim, which
 * makes the text and background transparent, so reset writes the theme's default colours.
 */
class CustomizeColorsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityCustomizeColorsBinding::inflate)
    private val iconSwitcher by lazy { AppIconSwitcher(this) }

    private companion object {
        const val DISABLED_ROW_ALPHA = 0.6f
        const val HINT_ALPHA = 0.7f
        const val MIN_READABLE_CONTRAST = 3.0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.colorsNestedScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.colorsNestedScrollview,
            topAppBar = binding.colorsAppbar
        )
        setupSystemThemeRow()
        healTransparentColors()

        binding.colorsResetHolder.setOnClickListener { resetColors() }
        binding.colorsPrimaryHolder.setOnClickListener {
            pickColor(config.primaryColor, getProperPrimaryColor()) { config.primaryColor = it }
        }
        binding.colorsTextHolder.setOnClickListener {
            pickColor(config.textColor, getProperTextColor()) {
                config.textColor = it
                keepPairReadable(pickedBackground = false)
            }
        }
        binding.colorsBackgroundHolder.setOnClickListener {
            pickColor(config.backgroundColor, getProperBackgroundColor()) {
                config.backgroundColor = it
                keepPairReadable(pickedBackground = true)
            }
        }
        binding.colorsSwapHolder.setOnClickListener { swapTextAndBackground() }
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

    /**
     * Commons draws a stored 0 verbatim rather than treating it as unset, so a reset has to write
     * the theme's own default colours. Zeros would leave the text and background transparent. The
     * launcher icon is deliberately untouched: it is not one of these colours and does not follow
     * the system theme either, so it has its own section on the screen.
     */
    /**
     * Flips text and background, which is the quickest way between a light and a dark screen.
     * The resolved colours are used rather than the stored ones, so this also works while a
     * colour is still unset, and the pair stays exactly as readable as it was.
     */
    private fun swapTextAndBackground() {
        val text = getProperTextColor()
        val background = getProperBackgroundColor()
        config.textColor = background
        config.backgroundColor = text
        toast(R.string.colors_swapped)
        recreate()
    }

    private fun resetColors() {
        applyDefaultColors()
        recreate()
    }

    private fun applyDefaultColors() {
        config.primaryColor = ContextCompat.getColor(this, org.fossify.commons.R.color.default_primary_color)
        config.textColor = ContextCompat.getColor(this, org.fossify.commons.R.color.default_text_color)
        config.backgroundColor = ContextCompat.getColor(this, org.fossify.commons.R.color.default_background_color)
    }

    /** 0 is what an earlier version of the reset action wrote, and it renders as transparent. */
    private fun healTransparentColors() {
        if (config.primaryColor == 0 || config.textColor == 0 || config.backgroundColor == 0) {
            applyDefaultColors()
            updateColors()
        }
    }

    private fun setupSystemThemeRow() {
        binding.colorsSystemThemeHolder.beVisibleIf(isSPlus())
        // the switch style is not clickable on its own, commons' own settings rows toggle it from
        // the row, so tapping anywhere on the row has to do the same here
        binding.colorsSystemThemeHolder.setOnClickListener { binding.colorsSystemTheme.toggle() }
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
                    recreate()
                }
            }
        }.onFailure { toast("Colour picker failed: $it") }
    }

    /**
     * A pick can leave the text and background too close to read, which is easy to do here: the
     * stock dark text is nearly white. Only in that case is the other colour matched to the one
     * that was picked, so a background choice turns the app light or dark as expected.
     */
    private fun keepPairReadable(pickedBackground: Boolean) {
        if (contrastRatio(config.textColor, config.backgroundColor) >= MIN_READABLE_CONTRAST) {
            return
        }

        if (pickedBackground) {
            config.textColor = if (isLightColor(config.backgroundColor)) {
                ContextCompat.getColor(this, org.fossify.commons.R.color.dark_grey)
            } else {
                ContextCompat.getColor(this, org.fossify.commons.R.color.theme_dark_text_color)
            }
            toast(R.string.text_color_matched_to_background)
        } else {
            config.backgroundColor = if (isLightColor(config.textColor)) {
                ContextCompat.getColor(this, org.fossify.commons.R.color.theme_dark_background_color)
            } else {
                ContextCompat.getColor(this, org.fossify.commons.R.color.theme_light_background_color)
            }
            toast(R.string.background_color_matched_to_text)
        }
    }

    private fun updateColors() {
        updateTextColors(binding.colorsCoordinator)
        binding.colorsSystemTheme.isChecked = config.isSystemThemeEnabled
        binding.colorsSystemThemeHint.beVisibleIf(config.isSystemThemeEnabled)
        binding.colorsPrimarySwatch.setColorFilter(getProperPrimaryColor())
        binding.colorsTextSwatch.setColorFilter(getProperTextColor())
        binding.colorsBackgroundSwatch.setColorFilter(getProperBackgroundColor())
        binding.colorsIconSwatch.setColorFilter(iconSwitcher.current().color)

        // while the app follows the system theme these colours cannot take effect, so the rows and
        // the reset action are shown as unavailable instead of silently ignoring a pick
        val canPick = !config.isSystemThemeEnabled
        listOf(
            binding.colorsPrimaryHolder,
            binding.colorsTextHolder,
            binding.colorsBackgroundHolder
        ).forEach { row ->
            row.isEnabled = canPick
            row.alpha = if (canPick) 1f else DISABLED_ROW_ALPHA
        }
        binding.colorsSwapHolder.isEnabled = canPick
        binding.colorsSwapHolder.alpha = if (canPick) 1f else DISABLED_ROW_ALPHA
        binding.colorsSwapHint.alpha = if (canPick) HINT_ALPHA else DISABLED_ROW_ALPHA
        binding.colorsResetHolder.isEnabled = canPick
        binding.colorsResetHolder.alpha = if (canPick) 1f else DISABLED_ROW_ALPHA
    }
}
