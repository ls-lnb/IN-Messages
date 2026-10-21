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
import org.fossify.messages.models.ShortCodeSender

class SenderPickerAdapter(
    private val activity: SimpleActivity,
    private val selectedAddresses: HashSet<String>,
    var groupTitles: Map<String, String>,
    var matches: Map<String, String> = emptyMap(),
    private val onSelectionChanged: () -> Unit,
) : RecyclerView.Adapter<SenderPickerAdapter.ViewHolder>() {
    private val textColor = activity.getProperTextColor()
    private val primaryColor = activity.getProperPrimaryColor()
    private val fontSize = activity.getTextSize()
    var senders = ArrayList<ShortCodeSender>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private fun relatedText(evidence: String, preview: String): CharSequence {
        val reason = activity.getString(R.string.related_sender_match, evidence)
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSenderPickerBinding.inflate(activity.layoutInflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(senders[position])
    }

    override fun getItemCount() = senders.size

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
            val evidence = matches[addressKey]
            binding.senderPickerDetails.apply {
                val base = groupTitle?.let {
                    activity.getString(R.string.in_sender_group, it)
                } ?: sender.snippet
                text = if (evidence.isNullOrEmpty()) {
                    base
                } else {
                    // the reason sits on its own line and in italics, so it cannot be mistaken
                    // for part of the message preview underneath it
                    relatedText(evidence, base)
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
