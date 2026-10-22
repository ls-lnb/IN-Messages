package org.fossify.messages.adapters

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemSenderPickerBinding
import org.fossify.messages.helpers.MatchReason
import org.fossify.messages.helpers.SenderMatch
import org.fossify.messages.databinding.ItemSenderPickerFooterBinding
import org.fossify.messages.models.ShortCodeSender

class SenderPickerAdapter(
    private val activity: SimpleActivity,
    private val selectedAddresses: HashSet<String>,
    var groupTitles: Map<String, String>,
    var matches: Map<String, SenderMatch> = emptyMap(),
    private val onSelectionChanged: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val textColor = activity.getProperTextColor()
    private val primaryColor = activity.getProperPrimaryColor()
    private val fontSize = activity.getTextSize()
    var senders = ArrayList<ShortCodeSender>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /** Closing note after the last row; null when the list should end with a sender. */
    var footerText: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class FooterHolder(private val binding: ItemSenderPickerFooterBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(text: String) {
            binding.senderPickerFooter.text = text
            binding.senderPickerFooter.setTextColor(textColor)
        }
    }

    private fun reasonText(match: SenderMatch): String {
        return if (match.reason == MatchReason.TYPO) {
            match.evidence
        } else {
            activity.getString(R.string.related_sender_match, match.evidence)
        }
    }

    private fun relatedText(reason: String, preview: String): CharSequence {
        if (preview.isBlank()) {
            return italic(reason)
        }

        val text = SpannableString("$reason\n$preview")
        text.setSpan(
            StyleSpan(Typeface.ITALIC),
            0,
            reason.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return text
    }

    private fun italic(value: String): CharSequence {
        val text = SpannableString(value)
        text.setSpan(StyleSpan(Typeface.ITALIC), 0, value.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text
    }

    private companion object {
        const val ADDRESS_SCALE = 1.1f
        const val DETAILS_SCALE = 0.85f
        const val TYPE_SENDER = 0
        const val TYPE_FOOTER = 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_FOOTER) {
            FooterHolder(ItemSenderPickerFooterBinding.inflate(activity.layoutInflater, parent, false))
        } else {
            ViewHolder(ItemSenderPickerBinding.inflate(activity.layoutInflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is FooterHolder -> holder.bind(footerText.orEmpty())
            is ViewHolder -> holder.bind(senders[position])
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == senders.size) TYPE_FOOTER else TYPE_SENDER
    }

    override fun getItemCount() = senders.size + if (footerText == null) 0 else 1

    inner class ViewHolder(private val binding: ItemSenderPickerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(sender: ShortCodeSender) {
            val addressKey = sender.address.uppercase()
            val groupTitle = groupTitles[addressKey]
            binding.senderPickerAddress.apply {
                text = sender.address
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * ADDRESS_SCALE)
            }
            val match = matches[addressKey]
            binding.senderPickerDetails.apply {
                val base = groupTitle?.let {
                    activity.getString(R.string.in_sender_group, it)
                } ?: sender.snippet
                text = if (match == null) {
                    base
                } else {
                    // the reason sits on its own line and in italics, so it cannot be mistaken
                    // for part of the message preview underneath it
                    relatedText(reasonText(match), base)
                }
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * DETAILS_SCALE)
            }
            binding.senderPickerCheck.apply {
                isChecked = selectedAddresses.contains(addressKey)
                buttonTintList = ColorStateList.valueOf(primaryColor)
            }
            binding.root.setOnClickListener {
                if (selectedAddresses.contains(addressKey)) {
                    selectedAddresses.remove(addressKey)
                } else {
                    selectedAddresses.add(addressKey)
                }
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    notifyItemChanged(position)
                }
                onSelectionChanged()
            }
        }
    }
}
