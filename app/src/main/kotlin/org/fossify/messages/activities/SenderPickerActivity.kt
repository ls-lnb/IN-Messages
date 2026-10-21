package org.fossify.messages.activities

import android.os.Bundle
import com.google.android.material.tabs.TabLayout
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.adapters.SenderPickerAdapter
import org.fossify.messages.databinding.ActivitySenderPickerBinding
import org.fossify.messages.dialogs.GroupSendersDialog
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.getShortCodeSenderAddresses
import org.fossify.messages.extensions.saveSenderGroupFromSelection
import org.fossify.messages.extensions.similarShortCodeKey
import org.fossify.messages.helpers.PRESELECTED_SENDER_ADDRESSES
import org.fossify.messages.helpers.SIMILAR_SEED_ADDRESS
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.helpers.SenderIndex
import org.fossify.messages.helpers.SenderSearch
import org.fossify.messages.models.ShortCodeSender

class SenderPickerActivity : SimpleActivity() {
    private val binding by viewBinding(ActivitySenderPickerBinding::inflate)
    private val selectedAddresses = HashSet<String>()

    // senders that belong to the group being created/edited, shown in the grouped tab
    private val memberAddresses = HashSet<String>()
    private var allSenders = ArrayList<ShortCodeSender>()
    private var groupTitles = emptyMap<String, String>()
    private var adapter: SenderPickerAdapter? = null
    private var currentQuery = ""
    private var seedAddress: String? = null
    private var senderIndex: SenderIndex? = null
    private var currentTab = TAB_SEARCH_SENDERS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.senderPickerList))
        setupMaterialScrollListener(
            scrollingView = binding.senderPickerList,
            topAppBar = binding.senderPickerAppbar
        )

        intent.getStringArrayListExtra(PRESELECTED_SENDER_ADDRESSES)
            ?.map { it.uppercase() }
            ?.let {
                selectedAddresses.addAll(it)
                memberAddresses.addAll(it)
            }
        seedAddress = intent.getStringExtra(SIMILAR_SEED_ADDRESS)

        // show the group name in the header when editing an existing group
        memberAddresses.firstNotNullOfOrNull { config.findSenderGroupByAddress(it) }?.let { group ->
            binding.senderPickerToolbar.title = group.title
        }

        binding.senderPickerSearch.onTextChangeListener { text ->
            currentQuery = text
            showFilteredSenders()
        }

        binding.senderPickerTabs.addTab(
            binding.senderPickerTabs.newTab().setText(R.string.search_senders)
        )
        binding.senderPickerTabs.addTab(
            binding.senderPickerTabs.newTab().setText(R.string.grouped_senders)
        )
        binding.senderPickerTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                showFilteredSenders()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        updateSelectedCount()
        loadSenders()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.senderPickerAppbar, NavigationIcon.Arrow)
        binding.senderPickerProgress.setIndicatorColor(getProperPrimaryColor())
    }

    private fun setupOptionsMenu() {
        binding.senderPickerToolbar.inflateMenu(R.menu.menu_sender_picker)
        binding.senderPickerToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.confirm_group_senders -> {
                    confirmSelection()
                    true
                }

                else -> false
            }
        }
    }

    private fun loadSenders() {
        binding.senderPickerProgress.show()
        ensureBackgroundThread {
            val snippets = HashMap<String, String>()
            try {
                (conversationsDB.getNonArchived() + conversationsDB.getAllArchived()).forEach { conversation ->
                    snippets[conversation.phoneNumber.uppercase()] = conversation.snippet
                }
            } catch (_: Exception) {
            }

            val senders = getShortCodeSenderAddresses()
                .map { address ->
                    ShortCodeSender(
                        address = address,
                        snippet = snippets[address.uppercase()].orEmpty()
                    )
                }
                .sortedWith(
                    compareBy<ShortCodeSender> {
                        it.address.similarShortCodeKey() ?: it.address.uppercase()
                    }.thenBy { it.address.uppercase() }
                )

            senderIndex = SenderIndex(senders.map { it.address })

            runOnUiThread {
                binding.senderPickerProgress.hide()
                allSenders = ArrayList(senders)
                groupTitles = buildGroupTitles()
                setupAdapter()
                showFilteredSenders()
                updateSelectedCount()
            }
        }
    }

    private fun setupAdapter() {
        adapter = SenderPickerAdapter(
            activity = this,
            selectedAddresses = selectedAddresses,
            groupTitles = groupTitles,
            onSelectionChanged = { updateSelectedCount() }
        )
        binding.senderPickerList.adapter = adapter
    }

    private fun showFilteredSenders() {
        val inGroupTab = currentTab == TAB_GROUPED_SENDERS
        val source = if (inGroupTab) {
            memberAddresses
                .map { address ->
                    allSenders.find { it.address.uppercase() == address }
                        ?: ShortCodeSender(address = address)
                }
                .sortedWith(
                    compareBy<ShortCodeSender> { it.address.similarShortCodeKey() ?: it.address.uppercase() }
                        .thenBy { it.address.uppercase() }
                )
        } else {
            allSenders.filterNot { memberAddresses.contains(it.address.uppercase()) }
        }

        val query = currentQuery.trim()
        val filtered: List<ShortCodeSender>
        val matches: Map<String, String>
        if (query.isEmpty()) {
            val ordered = SenderSearch(senderIndex, seedAddress).order(source)
            filtered = ordered.first
            matches = ordered.second
        } else {
            val search = SenderSearch(senderIndex, seedAddress).search(source, query, groupTitles)
            filtered = search.first
            matches = search.second
        }

        // the grouped tab shows message snippets instead of repeating the group name
        adapter?.groupTitles = if (inGroupTab) emptyMap() else groupTitles
        adapter?.matches = matches
        adapter?.footerText = if (inGroupTab) null else getString(R.string.sender_search_footer)
        adapter?.senders = ArrayList(filtered)
        binding.senderPickerPlaceholder.beVisibleIf(filtered.isEmpty())
        binding.senderPickerList.beVisibleIf(filtered.isNotEmpty())
        if (allSenders.isEmpty()) {
            binding.senderPickerPlaceholder.text = getString(R.string.no_short_code_senders)
        } else {
            binding.senderPickerPlaceholder.text = getString(org.fossify.commons.R.string.no_items_found)
        }
    }

    private fun updateSelectedCount() {
        binding.senderPickerSelectedCount.text = getString(
            R.string.selected_senders_count,
            selectedAddresses.size
        )
    }

    private fun confirmSelection() {
        hideKeyboard()
        if (selectedAddresses.size < 2) {
            toast(R.string.select_at_least_two_senders)
            return
        }

        // the group being edited: any group the current members belong to
        val anchorGroup = memberAddresses.firstNotNullOfOrNull { address ->
            config.findSenderGroupByAddress(address)
        }
        if (anchorGroup != null) {
            applySelection(title = anchorGroup.title, preferredGroupId = anchorGroup.id)
        } else {
            val prefilledName = SenderIndex.sharedRun(selectedAddresses)
                ?: seedAddress?.similarShortCodeKey()
                ?: selectedAddresses.first()
            GroupSendersDialog(this, prefilledName) { name ->
                applySelection(title = name, preferredGroupId = null)
            }
        }
    }

    // applies the checkbox state; checked senders move into the grouped tab, unchecked ones out
    private fun applySelection(title: String, preferredGroupId: String?) {
        binding.senderPickerProgress.show()
        ensureBackgroundThread {
            saveSenderGroupFromSelection(
                addresses = selectedAddresses.toList(),
                title = title,
                preferredGroupId = preferredGroupId,
            )
            runOnUiThread {
                binding.senderPickerProgress.hide()
                binding.senderPickerToolbar.title = title
                memberAddresses.clear()
                memberAddresses.addAll(selectedAddresses)
                groupTitles = buildGroupTitles()
                toast(R.string.sender_group_saved)
                refreshConversations(cacheOnly = true)
                showFilteredSenders()
                updateSelectedCount()
            }
        }
    }

    private fun buildGroupTitles(): Map<String, String> {
        val titles = HashMap<String, String>()
        config.senderGroups.forEach { group ->
            group.addresses.forEach { address ->
                titles[address.uppercase()] = group.title
            }
        }
        return titles
    }

    private companion object {
        const val TAB_SEARCH_SENDERS = 0
        const val TAB_GROUPED_SENDERS = 1
    }
}
