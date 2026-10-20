package org.fossify.messages.dialogs

import android.app.Activity
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toHex
import org.fossify.messages.R
import org.fossify.messages.databinding.DialogAppColorPickerBinding
import org.fossify.messages.helpers.colorShades

/**
 * Colour picker in the style Fossify ships: a strip of colours where the selected one is taller,
 * optional shades of it on a second line, the hex value under it (long press to copy) and
 * Cancel / OK buttons.
 *
 * It is owned by this fork, so it does not depend on the Thank You app or on resources this app
 * does not define - which is what commons' own picker needs.
 */
class AppColorPickerDialog(
    private val activity: Activity,
    initialColor: Int,
    private val colors: List<Int>,
    private val iconPreview: Drawable? = null,
    private val showShades: Boolean = false,
    callback: (wasPositive: Boolean, color: Int) -> Unit,
) {
    private var selectedColor = initialColor
    private val binding = DialogAppColorPickerBinding.inflate(activity.layoutInflater)

    init {
        binding.pickerPreview.apply {
            if (iconPreview == null) {
                beGone()
            } else {
                beVisible()
                setImageDrawable(iconPreview)
            }
        }
        if (showShades) {
            binding.pickerLineTwo.beVisible()
        }
        binding.pickerHex.setOnLongClickListener {
            activity.copyToClipboard(selectedColor.toHex())
            true
        }

        selectColor(initialColor)

        activity.getAlertDialogBuilder()
            .setView(binding.root)
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> callback(true, selectedColor) }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply { activity.setupDialogStuff(binding.root, this) }
    }

    private fun selectColor(color: Int) {
        selectedColor = color
        binding.pickerHex.text = color.toHex()
        buildBands(binding.pickerLineOne, colors, color, ::selectColor)
        if (binding.pickerLineTwo.visibility == View.VISIBLE) {
            buildBands(binding.pickerLineTwo, colorShades(color, SHADE_COUNT), color, ::selectColor)
        }
    }

    private fun buildBands(row: LinearLayout, bandColors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
        row.removeAllViews()
        bandColors.forEach { color ->
            val band = View(activity).apply {
                val height = activity.resources.getDimensionPixelSize(
                    if (color == selected) R.dimen.color_band_selected_height else R.dimen.color_band_height
                )
                layoutParams = LinearLayout.LayoutParams(0, height, 1f)
                setBackgroundResource(R.drawable.color_picker_band)
                background.setTint(color)
                contentDescription = color.toHex()
                setOnClickListener { onPick(color) }
            }
            row.addView(band)
        }
    }

    companion object {
        private const val SHADE_COUNT = 10
    }
}
