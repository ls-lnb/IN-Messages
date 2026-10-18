package org.fossify.messages.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.messages.R
import org.fossify.messages.databinding.DialogAddOtpKeywordBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.extractOtpCode

/**
 * Adds or edits one of the user's own OTP formats. The optional sample message field shows
 * straight away whether the phrase picks the code out, so a format can be verified while typing.
 */
class AddOtpKeywordDialog(
    val activity: BaseSimpleActivity,
    private val originalKeyword: String? = null,
    val callback: () -> Unit,
) {
    private val binding = DialogAddOtpKeywordBinding.inflate(activity.layoutInflater)

    init {
        originalKeyword?.let { binding.addOtpKeywordEdittext.setText(it) }
        binding.addOtpKeywordEdittext.onTextChangeListener { updatePreview() }
        binding.addOtpKeywordSample.onTextChangeListener { updatePreview() }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    alertDialog.showKeyboard(binding.addOtpKeywordEdittext)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val keyword = binding.addOtpKeywordEdittext.value.trim()
                        if (keyword.isEmpty()) {
                            activity.toast(R.string.otp_keyword_required)
                            return@setOnClickListener
                        }

                        if (originalKeyword != null && keyword != originalKeyword) {
                            activity.config.removeOtpKeyword(originalKeyword)
                        }
                        activity.config.addOtpKeyword(keyword)
                        callback()
                        alertDialog.dismiss()
                    }
                }
            }
    }

    private fun updatePreview() {
        val sample = binding.addOtpKeywordSample.value
        if (sample.isBlank()) {
            binding.addOtpKeywordPreview.beGone()
            return
        }

        val keyword = binding.addOtpKeywordEdittext.value.trim()
        val code = sample.extractOtpCode(listOfNotNull(keyword.ifEmpty { null }))
        binding.addOtpKeywordPreview.apply {
            beVisible()
            text = if (code != null) {
                activity.getString(R.string.otp_keyword_preview, code)
            } else {
                activity.getString(R.string.otp_keyword_no_match)
            }
        }
    }
}
