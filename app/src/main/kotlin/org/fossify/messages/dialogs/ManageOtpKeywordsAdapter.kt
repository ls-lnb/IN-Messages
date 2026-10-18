package org.fossify.messages.dialogs

import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.messages.databinding.ItemManageOtpKeywordBinding
import org.fossify.messages.extensions.config

/**
 * The OTP keywords screen: read only built in formats, the user's own formats (tap to edit, the
 * trailing icon removes them) and a final row for adding another one. A plain adapter is enough,
 * the list needs no selection or drag support.
 */
class ManageOtpKeywordsAdapter(
    private val activity: BaseSimpleActivity,
    private val rows: List<OtpKeywordRow>,
    private val onKeywordRemoved: () -> Unit,
    private val onAddClicked: () -> Unit,
    private val itemClick: (Any) -> Unit,
) : RecyclerView.Adapter<ManageOtpKeywordsAdapter.ViewHolder>() {
    private val textColor = activity.getProperTextColor()
    private val primaryColor = activity.getProperPrimaryColor()
    private val headerTextSize = activity.getTextSize() * HEADER_TEXT_RATIO

    class ViewHolder(val binding: ItemManageOtpKeywordBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemManageOtpKeywordBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.apply {
            root.setupViewBackground(activity)
            manageOtpKeywordTitle.apply {
                text = rows[position].text
                setTextSize(TypedValue.COMPLEX_UNIT_PX, activity.getTextSize())
                setTypeface(null, Typeface.NORMAL)
                setTextColor(textColor)
            }
            manageOtpKeywordDelete.beGone()
            root.setOnClickListener(null)
            root.isClickable = false

            setupRow(rows[position])
        }
    }

    override fun getItemCount() = rows.size

    private fun ItemManageOtpKeywordBinding.setupRow(row: OtpKeywordRow) {
        val title = manageOtpKeywordTitle
        when (row.kind) {
            OtpKeywordRow.Kind.HEADER -> {
                title.setTextSize(TypedValue.COMPLEX_UNIT_PX, headerTextSize)
                title.setTypeface(null, Typeface.BOLD)
                title.setTextColor(primaryColor)
            }

            OtpKeywordRow.Kind.BUILT_IN -> title.setTextColor(textColor.adjustAlpha(DISABLED_ALPHA))

            OtpKeywordRow.Kind.CUSTOM -> {
                root.isClickable = true
                root.setOnClickListener { itemClick(row.text) }
                manageOtpKeywordDelete.beVisible()
                manageOtpKeywordDelete.drawable?.mutate()
                manageOtpKeywordDelete.drawable?.setTint(textColor)
                manageOtpKeywordDelete.setOnClickListener {
                    activity.config.removeOtpKeyword(row.text)
                    onKeywordRemoved()
                }
            }

            OtpKeywordRow.Kind.ADD -> {
                title.setTextColor(primaryColor)
                root.isClickable = true
                root.setOnClickListener { onAddClicked() }
            }
        }
    }

    companion object {
        private const val HEADER_TEXT_RATIO = 0.85f
        private const val DISABLED_ALPHA = 0.6f
    }
}
