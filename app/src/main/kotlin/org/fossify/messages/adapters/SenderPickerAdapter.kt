package org.fossify.messages.adapters

import android.content.res.ColorStateList
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
                    listOf(base, activity.getString(R.string.related_sender_match, evidence))
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
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
