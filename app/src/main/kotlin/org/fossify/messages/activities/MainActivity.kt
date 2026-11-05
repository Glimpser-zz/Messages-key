package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.Telephony
import android.text.TextUtils
import androidx.appcompat.content.res.AppCompatResources
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkAppSideloading
import org.fossify.commons.extensions.checkWhatsNew
import org.fossify.commons.extensions.convertToBitmap
import org.fossify.commons.extensions.fadeIn
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.LICENSE_EVENT_BUS
import org.fossify.commons.helpers.LICENSE_INDICATOR_FAST_SCROLL
import org.fossify.commons.helpers.LICENSE_SMS_MMS
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.PERMISSION_READ_SMS
import org.fossify.commons.helpers.PERMISSION_SEND_SMS
import org.fossify.commons.helpers.SHORT_ANIMATION_DURATION
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.Release
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.adapters.ConversationsAdapter
import org.fossify.messages.adapters.RecycleBinConversationsAdapter
import org.fossify.messages.adapters.SearchResultsAdapter
import org.fossify.messages.databinding.ActivityMainBinding
import org.fossify.messages.extensions.checkAndDeleteOldRecycleBinMessages
import org.fossify.messages.extensions.clearAllMessagesIfNeeded
import org.fossify.messages.extensions.clearExpiredScheduledMessages
import org.fossify.messages.extensions.emptyMessagesRecycleBin
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.getConversations
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.insertOrUpdateConversation
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.helpers.SpamReminderScheduler
import org.fossify.messages.helpers.SEARCHED_MESSAGE_ID
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.fossify.messages.models.Message
import org.fossify.messages.models.SearchResult
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@Suppress("LargeClass", "MagicNumber")
class MainActivity : SimpleActivity() {
    companion object {
        const val EXTRA_OPEN_SPAM_TAB = "open_spam_tab"
    }
    override var isSearchBarEnabled = true
    
    private val MAKE_DEFAULT_APP_REQUEST = 1

    private var storedTextColor = 0
    private var storedFontSize = 0
    private var lastSearchedText = ""
    private var allConversations = arrayListOf<Conversation>()
    private var selectedConversationCategory = 0
    private var companyCategoryNames: List<String> = emptyList()
    private var bus: EventBus? = null

    private val trashTabIndex: Int
        get() = if (config.useRecycleBin) 6 else -1
    private val firstCompanyTabIndex: Int
        get() = if (config.useRecycleBin) 7 else 6

    private val binding by viewBinding(ActivityMainBinding::inflate)

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        if (intent.getBooleanExtra(EXTRA_OPEN_SPAM_TAB, false)) {
            selectedConversationCategory = 1
        }
        setupOptionsMenu()
        setupConversationCategoryTabs()
        refreshMenuItems()

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList))

        checkAndDeleteOldRecycleBinMessages()
        clearAllMessagesIfNeeded {
            loadMessages()
        }

        if (checkAppSideloading()) {
            return
        }
    }

    override fun onResume() {
        super.onResume()
        updateMenuColors()
        refreshMenuItems()

        val activeAdapter = if (selectedConversationCategory == trashTabIndex && trashTabIndex >= 0) {
            getOrCreateRecycleBinAdapter()
        } else {
            getOrCreateConversationsAdapter()
        }
        activeAdapter.apply {
            if (storedTextColor != getProperTextColor()) updateTextColor(getProperTextColor())
            if (storedFontSize != config.fontSize) updateFontSize()
            updateDrafts()
        }

        updateTextColors(binding.mainCoordinator)
        binding.searchHolder.setBackgroundColor(getProperBackgroundColor())

        val properPrimaryColor = getProperPrimaryColor()
        binding.noConversationsPlaceholder2.setTextColor(properPrimaryColor)
        binding.noConversationsPlaceholder2.underlineText()
        binding.conversationsFastscroller.updateColors(properPrimaryColor)
        binding.conversationsProgressBar.setIndicatorColor(properPrimaryColor)
        binding.conversationsProgressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)
        checkShortcut()
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
            true
        } else {
            appLockManager.lock()
            false
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.requireToolbar().inflateMenu(R.menu.menu_main)
        binding.mainMenu.toggleHideOnScroll(true)
        binding.mainMenu.setupMenu()

        binding.mainMenu.onSearchClosedListener = {
            fadeOutSearch()
        }

        binding.mainMenu.onSearchTextChangedListener = { text ->
            if (text.isNotEmpty()) {
                if (binding.searchHolder.alpha < 1f) {
                    binding.searchHolder.fadeIn()
                }
            } else {
                fadeOutSearch()
            }
            searchTextChanged(text)
        }

        binding.mainMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.show_recycle_bin -> launchRecycleBin()
                R.id.empty_recycle_bin -> emptyRecycleBin()
                R.id.show_archived -> launchArchivedConversations()
                R.id.spam_reminders -> showSpamReminderSettings()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun showSpamReminderSettings() {
        val intervals = intArrayOf(1, 4, 8, 12, 24)
        var selectedIndex = intervals.indexOf(config.spamReminderIntervalHours).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.spam_reminder_settings)
            .setSingleChoiceItems(R.array.spam_reminder_intervals, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(R.string.enable_spam_reminders) { _, _ ->
                config.spamReminderIntervalHours = intervals[selectedIndex]
                config.spamReminderEnabled = true
                SpamReminderScheduler.scheduleNext(this)
                toast(R.string.spam_reminders_enabled)
            }
            .setNeutralButton(R.string.disable_spam_reminders) { _, _ ->
                config.spamReminderEnabled = false
                SpamReminderScheduler.cancel(this)
                toast(R.string.spam_reminders_disabled)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_SPAM_TAB, false)) {
            selectedConversationCategory = 1
            binding.conversationCategoryTabs.getTabAt(1)?.select()
        }
    }

    private fun refreshMenuItems() {
        binding.mainMenu.requireToolbar().menu.apply {
            findItem(R.id.show_recycle_bin).isVisible = config.useRecycleBin
            findItem(R.id.show_archived).isVisible = config.isArchiveAvailable
            findItem(R.id.empty_recycle_bin).isVisible =
                config.useRecycleBin && selectedConversationCategory == trashTabIndex
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == MAKE_DEFAULT_APP_REQUEST) {
            if (resultCode == RESULT_OK) {
                askPermissions()
            } else {
                finish()
            }
        }
    }

    private fun storeStateVariables() {
        storedTextColor = getProperTextColor()
        storedFontSize = config.fontSize
    }

    private fun updateMenuColors() {
        binding.mainMenu.updateColors()
    }

    private fun loadMessages() {
        if (isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager!!.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    askPermissions()
                } else {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
                }
            } else {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                finish()
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                askPermissions()
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
            }
        }
    }

    // while SEND_SMS and READ_SMS permissions are mandatory, READ_CONTACTS is optional.
    // If we don't have it, we just won't be able to show the contact name in some cases
    private fun askPermissions() {
        handlePermission(PERMISSION_READ_SMS) {
            if (it) {
                handlePermission(PERMISSION_SEND_SMS) {
                    if (it) {
                        handlePermission(PERMISSION_READ_CONTACTS) {
                            handleNotificationPermission { granted ->
                                if (!granted) {
                                    PermissionRequiredDialog(
                                        activity = this,
                                        textId = org.fossify.commons.R.string.allow_notifications_incoming_messages,
                                        positiveActionCallback = { openNotificationSettings() })
                                }
                            }

                            initMessenger()
                            bus = EventBus.getDefault()
                            try {
                                bus!!.register(this)
                            } catch (_: Exception) {
                            }
                        }
                    } else {
                        finish()
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun initMessenger() {
        checkWhatsNewDialog()
        storeStateVariables()
        getCachedConversations()
        binding.noConversationsPlaceholder2.setOnClickListener {
            launchNewConversation()
        }

        binding.conversationsFab.setOnClickListener {
            launchNewConversation()
        }
    }

    private fun getCachedConversations() {
        ensureBackgroundThread {
            val conversations = try {
                conversationsDB.getNonArchived().toMutableList() as ArrayList<Conversation>
            } catch (_: Exception) {
                ArrayList()
            }

            val archived = try {
                conversationsDB.getAllArchived()
            } catch (_: Exception) {
                listOf()
            }

            runOnUiThread {
                setupConversations(conversations, cached = true)
                getNewConversations(
                    (conversations + archived).toMutableList() as ArrayList<Conversation>
                )
            }
            conversations.forEach {
                clearExpiredScheduledMessages(it.threadId)
            }
        }
    }

    private fun getNewConversations(cachedConversations: ArrayList<Conversation>) {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val conversations = getConversations(privateContacts = privateContacts)

            conversations.forEach { clonedConversation ->
                val threadIds = cachedConversations.map { it.threadId }
                if (!threadIds.contains(clonedConversation.threadId)) {
                    conversationsDB.insertOrUpdate(clonedConversation)
                    cachedConversations.add(clonedConversation)
                }
            }

            cachedConversations.forEach { cachedConversation ->
                val threadId = cachedConversation.threadId

                val isTemporaryThread = cachedConversation.isScheduled
                val isConversationDeleted = !conversations.map { it.threadId }.contains(threadId)
                if (isConversationDeleted && !isTemporaryThread) {
                    conversationsDB.deleteThreadId(threadId)
                }

                val newConversation =
                    conversations.find { it.phoneNumber == cachedConversation.phoneNumber }
                if (isTemporaryThread && newConversation != null) {
                    // delete the original temporary thread and move any scheduled messages
                    // to the new thread
                    conversationsDB.deleteThreadId(threadId)
                    messagesDB.getScheduledThreadMessages(threadId)
                        .forEach { message ->
                            messagesDB.insertOrUpdate(
                                message.copy(threadId = newConversation.threadId)
                            )
                        }
                    insertOrUpdateConversation(newConversation, cachedConversation)
                }
            }

            cachedConversations.forEach { cachedConv ->
                val conv = conversations.find {
                    it.threadId == cachedConv.threadId && !Conversation.areContentsTheSame(
                        old = cachedConv, new = it
                    )
                }
                if (conv != null) {
                    // FIXME: Scheduled message date is being reset here. Conversations with
                    //  scheduled messages will have their original date.
                    insertOrUpdateConversation(conv)
                }
            }

            val allConversations = conversationsDB.getNonArchived() as ArrayList<Conversation>
            runOnUiThread {
                setupConversations(allConversations)
            }

            if (config.appRunCount == 1) {
                conversations.map { it.threadId }.forEach { threadId ->
                    val messages = getMessages(threadId, includeScheduledMessages = false)
                    messages.chunked(30).forEach { currentMessages ->
                        messagesDB.insertMessages(*currentMessages.toTypedArray())
                    }
                }
            }
        }
    }

    private fun getOrCreateConversationsAdapter(): ConversationsAdapter {
        if (binding.conversationsList.adapter !is ConversationsAdapter) {
            binding.conversationsList.adapter = null
        }
        var currAdapter = binding.conversationsList.adapter
        if (currAdapter == null) {
            hideKeyboard()
            currAdapter = ConversationsAdapter(
                activity = this,
                recyclerView = binding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )

            binding.conversationsList.adapter = currAdapter
            if (areSystemAnimationsEnabled) {
                binding.conversationsList.scheduleLayoutAnimation()
            }
        }
        return currAdapter as ConversationsAdapter
    }

    private fun getOrCreateRecycleBinAdapter(): RecycleBinConversationsAdapter {
        if (binding.conversationsList.adapter !is RecycleBinConversationsAdapter) {
            binding.conversationsList.adapter = null
        }
        var currAdapter = binding.conversationsList.adapter
        if (currAdapter == null) {
            hideKeyboard()
            currAdapter = RecycleBinConversationsAdapter(
                activity = this,
                recyclerView = binding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )
            binding.conversationsList.adapter = currAdapter
            if (areSystemAnimationsEnabled) binding.conversationsList.scheduleLayoutAnimation()
        }
        return currAdapter as RecycleBinConversationsAdapter
    }

    private fun setupConversations(
        conversations: ArrayList<Conversation>,
        cached: Boolean = false,
    ) {
        allConversations = conversations
            .sortedWith(
                compareByDescending<Conversation> {
                    config.pinnedConversations.contains(it.threadId.toString())
                }.thenByDescending { it.date }
            ).toMutableList() as ArrayList<Conversation>
        updateCompanyCategoryTabs()
        if (trashTabIndex >= 0 && selectedConversationCategory == trashTabIndex) {
            showOrHideProgress(false)
            loadTrashConversations()
            return
        }
        binding.conversationsFab.beVisible()
        val sortedConversations = getConversationsForSelectedCategory()

        if (cached && config.appRunCount == 1) {
            // there are no cached conversations on the first run so we show the
            // loading placeholder and progress until we are done loading from telephony
            showOrHideProgress(conversations.isEmpty())
        } else {
            showOrHideProgress(false)
            showOrHidePlaceholder(conversations.isEmpty())
        }

        try {
            getOrCreateConversationsAdapter().apply {
                updateConversations(sortedConversations) {
                    if (!cached) {
                        showOrHidePlaceholder(currentList.isEmpty())
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun setupConversationCategoryTabs() {
        companyCategoryNames = config.companyAssignments.values.distinct().sorted()
        binding.conversationCategoryTabs.apply {
            removeAllTabs()
            addTab(newTab().setText(R.string.all_conversations))
            addTab(newTab().setText(R.string.spam_conversations))
            addTab(newTab().setText(R.string.company_conversations))
            addTab(newTab().setText(R.string.contact_conversations))
            addTab(newTab().setText(R.string.personal_conversations))
            addTab(newTab().setText(R.string.other_conversations))
            if (config.useRecycleBin) addTab(newTab().setText(R.string.recycle_bin))
            companyCategoryNames.forEach { company ->
                addTab(newTab().setText(company))
            }
            getTabAt(selectedConversationCategory)?.select()
            addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                    selectedConversationCategory = tab.position
                    showFilteredConversations()
                }
                override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
                override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
            })
        }
    }

    private fun updateCompanyCategoryTabs() {
        val newNames = config.companyAssignments.values.distinct().sorted()
        val selectedCompany = companyCategoryNames.getOrNull(selectedConversationCategory - firstCompanyTabIndex)
        if (newNames != companyCategoryNames) {
            val tabs = binding.conversationCategoryTabs
            while (tabs.tabCount > firstCompanyTabIndex) {
                tabs.removeTabAt(tabs.tabCount - 1)
            }
            companyCategoryNames = newNames
            companyCategoryNames.forEach { company ->
                tabs.addTab(tabs.newTab().setText(company))
            }
            val preservedIndex = selectedCompany?.let { companyCategoryNames.indexOf(it) }
                ?.takeIf { it >= 0 }?.plus(firstCompanyTabIndex)
            if (selectedCompany != null) {
                selectedConversationCategory = preservedIndex ?: 2
                tabs.getTabAt(selectedConversationCategory)?.select()
            }
        }
        companyCategoryNames.forEachIndexed { index, company ->
            val unreadCount = allConversations
                .filter {
                    config.companyAssignments[it.phoneNumber] == company &&
                        !config.spamConversations.contains(it.phoneNumber) &&
                        !config.personalConversations.contains(it.phoneNumber)
                }
                .sumBy { it.unreadCount }
            val label = if (unreadCount > 0) "$company ($unreadCount)" else company
            binding.conversationCategoryTabs.getTabAt(index + firstCompanyTabIndex)?.text = label
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun getConversationsForSelectedCategory(): ArrayList<Conversation> {
        val personal = config.personalConversations
        val spam = config.spamConversations
        val selectedCompany = companyCategoryNames.getOrNull(selectedConversationCategory - firstCompanyTabIndex)
        val filtered = when (selectedConversationCategory) {
            1 -> allConversations.filter { spam.contains(it.phoneNumber) && !personal.contains(it.phoneNumber) }
            2 -> allConversations.filter {
                config.companyAssignments.containsKey(it.phoneNumber) &&
                    !personal.contains(it.phoneNumber) && !spam.contains(it.phoneNumber)
            }
            3 -> allConversations.filter {
                !personal.contains(it.phoneNumber) && !spam.contains(it.phoneNumber) &&
                    !config.companyAssignments.containsKey(it.phoneNumber) &&
                    !it.isGroupConversation && it.title.isNotBlank() && it.title != it.phoneNumber
            }
            4 -> allConversations.filter { personal.contains(it.phoneNumber) }
            5 -> allConversations.filter { conversation ->
                !personal.contains(conversation.phoneNumber) &&
                    !spam.contains(conversation.phoneNumber) &&
                    !config.companyAssignments.containsKey(conversation.phoneNumber) &&
                    (conversation.isGroupConversation ||
                        conversation.title.isBlank() ||
                        conversation.title == conversation.phoneNumber)
            }
            else -> if (selectedCompany != null) {
                allConversations.filter {
                    config.companyAssignments[it.phoneNumber] == selectedCompany &&
                        !personal.contains(it.phoneNumber) && !spam.contains(it.phoneNumber)
                }
            } else {
                allConversations
            }
        }
        return ArrayList(filtered)
    }

    private fun showFilteredConversations() {
        if (trashTabIndex >= 0 && selectedConversationCategory == trashTabIndex) {
            binding.conversationsFab.beGone()
            binding.noConversationsPlaceholder2.beGone()
            refreshMenuItems()
            loadTrashConversations()
            return
        }
        binding.conversationsFab.beVisible()
        refreshMenuItems()
        val filtered = getConversationsForSelectedCategory()
        getOrCreateConversationsAdapter().updateConversations(filtered) {
            showOrHidePlaceholder(filtered.isEmpty())
        }
    }

    private fun showOrHideProgress(show: Boolean) {
        if (show) {
            binding.conversationsProgressBar.show()
            binding.noConversationsPlaceholder.beVisible()
            binding.noConversationsPlaceholder.text = getString(R.string.loading_messages)
        } else {
            binding.conversationsProgressBar.hide()
            binding.noConversationsPlaceholder.beGone()
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        binding.conversationsFastscroller.beGoneIf(show)
        binding.noConversationsPlaceholder.beVisibleIf(show)
        binding.noConversationsPlaceholder.text = getString(R.string.no_conversations_found)
        binding.noConversationsPlaceholder2.beVisibleIf(show)
    }

    private fun fadeOutSearch() {
        binding.searchHolder.animate()
            .alpha(0f)
            .setDuration(SHORT_ANIMATION_DURATION)
            .withEndAction {
                binding.searchHolder.beGone()
                searchTextChanged("", true)
            }.start()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        if (trashTabIndex >= 0 && selectedConversationCategory == trashTabIndex) {
            getOrCreateRecycleBinAdapter().notifyDataSetChanged()
        } else {
            getOrCreateConversationsAdapter().notifyDataSetChanged()
        }
    }

    private fun loadTrashConversations() {
        ensureBackgroundThread {
            val items = try {
                conversationsDB.getAllWithMessagesInRecycleBin().toMutableList() as ArrayList<Conversation>
            } catch (_: Exception) {
                ArrayList()
            }
            runOnUiThread {
                if (selectedConversationCategory != trashTabIndex) return@runOnUiThread
                val sorted = items.sortedWith(
                    compareByDescending<Conversation> { config.pinnedConversations.contains(it.threadId.toString()) }
                        .thenByDescending { it.date }
                ).toMutableList() as ArrayList<Conversation>
                binding.conversationsFab.beGone()
                binding.noConversationsPlaceholder2.beGone()
                showOrHidePlaceholder(sorted.isEmpty())
                binding.mainMenu.requireToolbar().menu.findItem(R.id.empty_recycle_bin).isVisible = sorted.isNotEmpty()
                getOrCreateRecycleBinAdapter().updateConversations(sorted)
            }
        }
    }

    private fun emptyRecycleBin() {
        ConfirmationDialog(
            activity = this,
            message = "",
            messageId = R.string.empty_recycle_bin_messages_confirmation,
            positive = org.fossify.commons.R.string.yes,
            negative = org.fossify.commons.R.string.no
        ) {
            ensureBackgroundThread {
                emptyMessagesRecycleBin()
                loadTrashConversations()
            }
        }
    }

    fun refreshConversationCategory() {
        showFilteredConversations()
    }

    private fun handleConversationClick(any: Any) {
        Intent(this, ThreadActivity::class.java).apply {
            val conversation = any as Conversation
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            if (trashTabIndex >= 0 && selectedConversationCategory == trashTabIndex) {
                putExtra(org.fossify.messages.helpers.IS_RECYCLE_BIN, true)
            }
            startActivity(this)
        }
    }

    private fun launchNewConversation() {
        hideKeyboard()
        Intent(this, NewConversationActivity::class.java).apply {
            startActivity(this)
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcut() {
        val appIconColor = config.appIconColor
        if (config.lastHandledShortcutColor != appIconColor) {
            val newConversation = getCreateNewContactShortcut(appIconColor)

            val manager = getSystemService(ShortcutManager::class.java)
            try {
                manager.dynamicShortcuts = listOf(newConversation)
                config.lastHandledShortcutColor = appIconColor
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.new_conversation)
        val drawable =
            AppCompatResources.getDrawable(this, org.fossify.commons.R.drawable.shortcut_plus)

        (drawable as LayerDrawable).findDrawableByLayerId(
            org.fossify.commons.R.id.shortcut_plus_background
        ).applyColorFilter(appIconColor)

        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, NewConversationActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "new_conversation")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .setRank(0)
            .build()
    }

    private fun searchTextChanged(text: String, forceUpdate: Boolean = false) {
        if (!binding.mainMenu.isSearchOpen && !forceUpdate) {
            return
        }

        lastSearchedText = text
        binding.searchPlaceholder2.beGoneIf(text.length >= 2)
        if (text.length >= 2) {
            ensureBackgroundThread {
                val searchQuery = "%$text%"
                val messages = messagesDB.getMessagesWithText(searchQuery)
                val conversations = conversationsDB.getConversationsWithText(searchQuery)
                if (text == lastSearchedText) {
                    showSearchResults(messages, conversations, text)
                }
            }
        } else {
            binding.searchPlaceholder.beVisible()
            binding.searchResultsList.beGone()
        }
    }

    private fun showSearchResults(
        messages: List<Message>,
        conversations: List<Conversation>,
        searchedText: String,
    ) {
        val searchResults = ArrayList<SearchResult>()
        conversations.forEach { conversation ->
            val date = (conversation.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )

            val searchResult = SearchResult(
                messageId = -1,
                title = conversation.title,
                snippet = conversation.phoneNumber,
                date = date,
                threadId = conversation.threadId,
                photoUri = conversation.photoUri
            )
            searchResults.add(searchResult)
        }

        messages.sortedByDescending { it.id }.forEach { message ->
            var recipient = message.senderName
            if (recipient.isEmpty() && message.participants.isNotEmpty()) {
                val participantNames = message.participants.map { it.name }
                recipient = TextUtils.join(", ", participantNames)
            }

            val date = (message.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )

            val searchResult = SearchResult(
                messageId = message.id,
                title = recipient,
                snippet = message.body,
                date = date,
                threadId = message.threadId,
                photoUri = message.senderPhotoUri
            )
            searchResults.add(searchResult)
        }

        runOnUiThread {
            binding.searchResultsList.beVisibleIf(searchResults.isNotEmpty())
            binding.searchPlaceholder.beVisibleIf(searchResults.isEmpty())

            val currAdapter = binding.searchResultsList.adapter
            if (currAdapter == null) {
                SearchResultsAdapter(this, searchResults, binding.searchResultsList, searchedText) {
                    hideKeyboard()
                    Intent(this, ThreadActivity::class.java).apply {
                        putExtra(THREAD_ID, (it as SearchResult).threadId)
                        putExtra(THREAD_TITLE, it.title)
                        putExtra(SEARCHED_MESSAGE_ID, it.messageId)
                        startActivity(this)
                    }
                }.apply {
                    binding.searchResultsList.adapter = this
                }
            } else {
                (currAdapter as SearchResultsAdapter).updateItems(searchResults, searchedText)
            }
        }
    }

    private fun launchRecycleBin() {
        hideKeyboard()
        startActivity(Intent(applicationContext, RecycleBinConversationsActivity::class.java))
    }

    private fun launchArchivedConversations() {
        hideKeyboard()
        startActivity(Intent(applicationContext, ArchivedConversationsActivity::class.java))
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_EVENT_BUS or LICENSE_SMS_MMS or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(
                title = R.string.faq_2_title,
                text = R.string.faq_2_text
            ),
            FAQItem(
                title = R.string.faq_3_title,
                text = R.string.faq_3_text
            ),
            FAQItem(
                title = R.string.faq_4_title,
                text = R.string.faq_4_text
            ),
            FAQItem(
                title = org.fossify.commons.R.string.faq_9_title_commons,
                text = org.fossify.commons.R.string.faq_9_text_commons
            )
        )

        if (resources.getBoolean(org.fossify.commons.R.bool.is_google_play_build)) {
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_2_title_commons,
                    text = org.fossify.commons.R.string.faq_2_text_commons
                )
            )
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_6_title_commons,
                    text = org.fossify.commons.R.string.faq_6_text_commons
                )
            )
        }

        startAboutActivity(
            appNameId = R.string.app_name,
            licenseMask = licenses,
            versionName = BuildConfig.VERSION_NAME,
            faqItems = faqItems,
            showFAQBeforeMail = true
        )
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshConversations(@Suppress("unused") event: Events.RefreshConversations) {
        initMessenger()
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
