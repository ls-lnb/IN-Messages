package org.fossify.messages.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.activities.FAQActivity
import org.fossify.commons.activities.LicenseActivity
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.launchViewIntent
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.LICENSE_EVENT_BUS
import org.fossify.commons.helpers.LICENSE_INDICATOR_FAST_SCROLL
import org.fossify.commons.helpers.LICENSE_SMS_MMS
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.FAQItem
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityAboutBinding
import org.fossify.messages.helpers.AppIconSwitcher

/**
 * The About screen is written here instead of taken from commons: commons' version is a compose
 * screen with Fossify's social links, its "Help us" section and its own support address baked in,
 * none of which can be overridden from resources. The FAQ entries stay, and the only outbound
 * links are this fork's repository and its issue tracker.
 */
class AboutActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityAboutBinding::inflate)

    private companion object {
        const val REPOSITORY_URL = "https://github.com/ls-lnb/messages-lnb"
        const val UPSTREAM_URL = "https://github.com/FossifyOrg/Messages"
        const val ISSUES_URL = "$REPOSITORY_URL/issues"

        // commons' FAQActivity and LicenseActivity read their input under these keys, and
        // expose no setters for them.
        const val EXTRA_FAQ = "app_faq"
        const val EXTRA_LICENSES = "app_licenses"
        val LICENSES = LICENSE_EVENT_BUS or LICENSE_SMS_MMS or LICENSE_INDICATOR_FAST_SCROLL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.aboutNestedScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.aboutNestedScrollview,
            topAppBar = binding.aboutAppbar
        )

        binding.aboutIcon.setImageDrawable(AppIconSwitcher(this).iconDrawable())
        binding.aboutVersion.text = getString(
            R.string.about_version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )

        binding.aboutForkCredit.setOnClickListener { launchViewIntent(UPSTREAM_URL) }
        binding.aboutFaqHolder.setOnClickListener { launchFaq() }
        binding.aboutIssuesHolder.setOnClickListener { launchViewIntent(ISSUES_URL) }
        binding.aboutGithubHolder.setOnClickListener { launchViewIntent(REPOSITORY_URL) }
        binding.aboutLicensesHolder.setOnClickListener { launchLicenses() }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.aboutAppbar, NavigationIcon.Arrow, topBarColor = getProperBackgroundColor())
        binding.aboutCoordinator.setBackgroundColor(getProperBackgroundColor())
        updateTextColors(binding.aboutNestedScrollview)
        binding.aboutForkCredit.setTextColor(getProperPrimaryColor())
    }

    private fun faqItems(): List<FAQItem> {
        val items = mutableListOf(
            FAQItem(R.string.faq_2_title, R.string.faq_2_text),
            FAQItem(R.string.faq_3_title, R.string.faq_3_text),
            FAQItem(R.string.faq_4_title, R.string.faq_4_text),
            FAQItem(
                org.fossify.commons.R.string.faq_9_title_commons,
                org.fossify.commons.R.string.faq_9_text_commons
            )
        )

        if (!resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)) {
            items.add(
                FAQItem(
                    org.fossify.commons.R.string.faq_2_title_commons,
                    org.fossify.commons.R.string.faq_2_text_commons
                )
            )
            items.add(
                FAQItem(
                    org.fossify.commons.R.string.faq_6_title_commons,
                    org.fossify.commons.R.string.faq_6_text_commons
                )
            )
        }
        return items
    }

    private fun launchFaq() {
        startActivity(
            Intent(this, FAQActivity::class.java)
                .putExtra(EXTRA_FAQ, ArrayList(faqItems()))
        )
    }
    private fun launchLicenses() {
        startActivity(
            Intent(this, LicenseActivity::class.java).putExtra(EXTRA_LICENSES, LICENSES)
        )
    }
}
