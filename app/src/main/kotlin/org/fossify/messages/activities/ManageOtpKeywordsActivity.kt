package org.fossify.messages.activities

import android.os.Bundle
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityManageOtpKeywordsBinding
import org.fossify.messages.dialogs.AddOtpKeywordDialog
import org.fossify.messages.dialogs.OtpKeywordRow
import org.fossify.messages.dialogs.ManageOtpKeywordsAdapter
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.otpBuiltInKeywords

/**
 * Manages the user's own OTP formats, the phrases that mark a code in messages. The built in
 * formats always apply; this only adds to them.
 */
class ManageOtpKeywordsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityManageOtpKeywordsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.manageOtpKeywordsList))
        setupMaterialScrollListener(
            scrollingView = binding.manageOtpKeywordsList,
            topAppBar = binding.otpKeywordsAppbar
        )
        updateTextColors(binding.manageOtpKeywordsWrapper)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.otpKeywordsAppbar, NavigationIcon.Arrow)
        updateKeywords()
    }

    private fun setupOptionsMenu() {
        binding.otpKeywordsToolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.add_otp_keyword) {
                addOrEditOtpKeyword()
                true
            } else {
                false
            }
        }
    }

    private fun updateKeywords() {
        val customKeywords = config.otpKeywords.sorted()
        val rows = ArrayList<OtpKeywordRow>()
        rows.add(OtpKeywordRow(getString(R.string.otp_keywords_built_in), OtpKeywordRow.Kind.HEADER))
        otpBuiltInKeywords.forEach { rows.add(OtpKeywordRow(it, OtpKeywordRow.Kind.BUILT_IN)) }
        rows.add(OtpKeywordRow(getString(R.string.otp_keywords_yours), OtpKeywordRow.Kind.HEADER))
        customKeywords.forEach { rows.add(OtpKeywordRow(it, OtpKeywordRow.Kind.CUSTOM)) }
        rows.add(OtpKeywordRow(getString(R.string.add_otp_keyword), OtpKeywordRow.Kind.ADD))

        ManageOtpKeywordsAdapter(
            activity = this,
            rows = rows,
            onKeywordRemoved = { updateKeywords() },
            onAddClicked = { addOrEditOtpKeyword() }
        ) {
            addOrEditOtpKeyword(it as? String)
        }.apply {
            binding.manageOtpKeywordsList.adapter = this
        }
    }

    private fun addOrEditOtpKeyword(keyword: String? = null) {
        AddOtpKeywordDialog(this, keyword) {
            updateKeywords()
        }
    }
}
