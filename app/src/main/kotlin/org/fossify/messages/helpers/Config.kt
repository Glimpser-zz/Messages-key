package org.fossify.messages.helpers

import android.content.Context
import org.fossify.commons.helpers.BaseConfig
import org.fossify.messages.extensions.getDefaultKeyboardHeight
import org.fossify.messages.models.Conversation

@Suppress("TooManyFunctions")
class Config(context: Context) : BaseConfig(context) {
    companion object {
        const val WORK_CONVERSATIONS = "work_conversations"
        const val PERSONAL_CONVERSATIONS = "personal_conversations"
        const val PERSONAL_KEYWORDS = "personal_keywords"
        const val SPAM_CONVERSATIONS = "spam_conversations"
        const val SPAM_WHITELIST = "spam_whitelist"
        const val COMPANY_ASSIGNMENTS = "company_assignments"
        const val SPAM_REMINDER_ENABLED = "spam_reminder_enabled"
        const val SPAM_REMINDER_INTERVAL_HOURS = "spam_reminder_interval_hours"
        private const val DEFAULT_SPAM_REMINDER_INTERVAL_HOURS = 24
        private const val MIN_SPAM_REMINDER_INTERVAL_HOURS = 1
        private const val MAX_SPAM_REMINDER_INTERVAL_HOURS = 168
        fun newInstance(context: Context) = Config(context)
    }

    fun saveUseSIMIdAtNumber(number: String, SIMId: Int) {
        prefs.edit().putInt(USE_SIM_ID_PREFIX + number, SIMId).apply()
    }

    fun getUseSIMIdAtNumber(number: String) = prefs.getInt(USE_SIM_ID_PREFIX + number, 0)

    var showCharacterCounter: Boolean
        get() = prefs.getBoolean(SHOW_CHARACTER_COUNTER, false)
        set(showCharacterCounter) = prefs.edit()
            .putBoolean(SHOW_CHARACTER_COUNTER, showCharacterCounter).apply()

    var useSimpleCharacters: Boolean
        get() = prefs.getBoolean(USE_SIMPLE_CHARACTERS, false)
        set(useSimpleCharacters) = prefs.edit()
            .putBoolean(USE_SIMPLE_CHARACTERS, useSimpleCharacters).apply()

    var sendOnEnter: Boolean
        get() = prefs.getBoolean(SEND_ON_ENTER, false)
        set(sendOnEnter) = prefs.edit().putBoolean(SEND_ON_ENTER, sendOnEnter).apply()

    var enableDeliveryReports: Boolean
        get() = prefs.getBoolean(ENABLE_DELIVERY_REPORTS, false)
        set(enableDeliveryReports) = prefs.edit()
            .putBoolean(ENABLE_DELIVERY_REPORTS, enableDeliveryReports).apply()

    var sendLongMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_LONG_MESSAGE_MMS, false)
        set(sendLongMessageMMS) = prefs.edit().putBoolean(SEND_LONG_MESSAGE_MMS, sendLongMessageMMS)
            .apply()

    var sendGroupMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_GROUP_MESSAGE_MMS, false)
        set(sendGroupMessageMMS) = prefs.edit()
            .putBoolean(SEND_GROUP_MESSAGE_MMS, sendGroupMessageMMS).apply()

    val isGroupMessageMmsPreferenceResolved: Boolean
        get() = prefs.contains(SEND_GROUP_MESSAGE_MMS)

    var lockScreenVisibilitySetting: Int
        get() = prefs.getInt(LOCK_SCREEN_VISIBILITY, LOCK_SCREEN_SENDER_MESSAGE)
        set(lockScreenVisibilitySetting) = prefs.edit()
            .putInt(LOCK_SCREEN_VISIBILITY, lockScreenVisibilitySetting).apply()

    var mmsFileSizeLimit: Long
        get() = prefs.getLong(MMS_FILE_SIZE_LIMIT, FILE_SIZE_200_KB)
        set(mmsFileSizeLimit) = prefs.edit().putLong(MMS_FILE_SIZE_LIMIT, mmsFileSizeLimit).apply()

    var pinnedConversations: Set<String>
        get() = prefs.getStringSet(PINNED_CONVERSATIONS, HashSet<String>())!!
        set(pinnedConversations) = prefs.edit()
            .putStringSet(PINNED_CONVERSATIONS, pinnedConversations).apply()

    fun addPinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.plus(threadId.toString())
    }

    fun addPinnedConversations(conversations: List<Conversation>) {
        pinnedConversations = pinnedConversations.plus(conversations.map { it.threadId.toString() })
    }

    fun removePinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.minus(threadId.toString())
    }

    fun removePinnedConversations(conversations: List<Conversation>) {
        pinnedConversations =
            pinnedConversations.minus(conversations.map { it.threadId.toString() })
    }

    // Conversation categories are keyed by phone number so they survive app restarts
    // and remain stable if the telephony thread ID changes.
    var workConversations: Set<String>
        get() = prefs.getStringSet(WORK_CONVERSATIONS, HashSet<String>())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(WORK_CONVERSATIONS, value.toSet()).apply()

    var personalConversations: Set<String>
        get() = prefs.getStringSet(PERSONAL_CONVERSATIONS, HashSet<String>())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(PERSONAL_CONVERSATIONS, value.toSet()).apply()

    var spamConversations: Set<String>
        get() = prefs.getStringSet(SPAM_CONVERSATIONS, HashSet<String>())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(SPAM_CONVERSATIONS, value.toSet()).apply()

    var spamWhitelist: Set<String>
        get() = prefs.getStringSet(SPAM_WHITELIST, HashSet<String>())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(SPAM_WHITELIST, value.toSet()).apply()

    fun addSpamWhitelistNumber(phoneNumber: String) {
        val number = phoneNumber.trim()
        if (number.isNotEmpty()) {
            spamWhitelist = spamWhitelist + number
            removeSpamConversation(number)
        }
    }

    fun removeSpamWhitelistNumber(phoneNumber: String) {
        spamWhitelist = spamWhitelist - phoneNumber
    }

    var spamReminderEnabled: Boolean
        get() = prefs.getBoolean(SPAM_REMINDER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(SPAM_REMINDER_ENABLED, value).apply()

    var spamReminderIntervalHours: Int
        get() = prefs.getInt(SPAM_REMINDER_INTERVAL_HOURS, DEFAULT_SPAM_REMINDER_INTERVAL_HOURS)
        set(value) = prefs.edit()
            .putInt(
                SPAM_REMINDER_INTERVAL_HOURS,
                value.coerceIn(MIN_SPAM_REMINDER_INTERVAL_HOURS, MAX_SPAM_REMINDER_INTERVAL_HOURS)
            ).apply()

    fun addSpamConversation(phoneNumber: String) {
        if (phoneNumber.isBlank()) return
        spamConversations = spamConversations + phoneNumber
        personalConversations = personalConversations - phoneNumber
        spamWhitelist = spamWhitelist - phoneNumber
        companyAssignments = companyAssignments - phoneNumber
    }

    fun removeSpamConversation(phoneNumber: String) {
        if (phoneNumber.isBlank()) return
        spamConversations = spamConversations - phoneNumber
    }

    // Stores one company name per sender number. JSON avoids delimiter collisions.
    var companyAssignments: Map<String, String>
        get() {
            val raw = prefs.getString(COMPANY_ASSIGNMENTS, "{}") ?: "{}"
            return try {
                val json = org.json.JSONObject(raw)
                val result = mutableMapOf<String, String>()
                json.keys().forEach { key ->
                    val value = json.optString(key).trim()
                    if (key.isNotBlank() && value.isNotBlank()) result[key] = value
                }
                result
            } catch (_: Exception) {
                emptyMap()
            }
        }
        set(value) {
            val json = org.json.JSONObject()
            value.forEach { (number, company) ->
                if (number.isNotBlank() && company.isNotBlank()) json.put(number, company.trim())
            }
            prefs.edit().putString(COMPANY_ASSIGNMENTS, json.toString()).apply()
        }

    fun assignConversationToCompany(phoneNumber: String, companyName: String) {
        val name = companyName.trim()
        if (phoneNumber.isBlank()) return
        companyAssignments = if (name.isEmpty()) {
            companyAssignments - phoneNumber
        } else {
            personalConversations = personalConversations - phoneNumber
            removeSpamConversation(phoneNumber)
            companyAssignments + (phoneNumber to name)
        }
    }

    fun addPersonalConversation(phoneNumber: String) {
        if (phoneNumber.isBlank()) return
        personalConversations = personalConversations + phoneNumber
        removeSpamConversation(phoneNumber)
        companyAssignments = companyAssignments - phoneNumber
    }

    fun removePersonalConversation(phoneNumber: String) {
        if (phoneNumber.isBlank()) return
        personalConversations = personalConversations - phoneNumber
    }

    // Keywords that automatically route the sender's entire conversation to Personal.
    // Kept separate from blockedKeywords so personal routing never acts as spam filtering.
    var personalKeywords: Set<String>
        get() = prefs.getStringSet(PERSONAL_KEYWORDS, HashSet<String>())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(PERSONAL_KEYWORDS, value.toSet()).apply()

    fun addPersonalKeyword(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isNotEmpty()) personalKeywords = personalKeywords + normalized
    }

    fun removePersonalKeyword(keyword: String) {
        personalKeywords = personalKeywords - keyword
    }

    var blockedKeywords: Set<String>
        get() = prefs.getStringSet(BLOCKED_KEYWORDS, HashSet<String>())!!
        set(blockedKeywords) = prefs.edit().putStringSet(BLOCKED_KEYWORDS, blockedKeywords).apply()

    fun addBlockedKeyword(keyword: String) {
        blockedKeywords = blockedKeywords.plus(keyword)
    }

    fun removeBlockedKeyword(keyword: String) {
        blockedKeywords = blockedKeywords.minus(keyword)
    }

    var exportSms: Boolean
        get() = prefs.getBoolean(EXPORT_SMS, true)
        set(exportSms) = prefs.edit().putBoolean(EXPORT_SMS, exportSms).apply()

    var exportMms: Boolean
        get() = prefs.getBoolean(EXPORT_MMS, true)
        set(exportMms) = prefs.edit().putBoolean(EXPORT_MMS, exportMms).apply()

    var importSms: Boolean
        get() = prefs.getBoolean(IMPORT_SMS, true)
        set(importSms) = prefs.edit().putBoolean(IMPORT_SMS, importSms).apply()

    var importMms: Boolean
        get() = prefs.getBoolean(IMPORT_MMS, true)
        set(importMms) = prefs.edit().putBoolean(IMPORT_MMS, importMms).apply()

    var wasDbCleared: Boolean
        get() = prefs.getBoolean(WAS_DB_CLEARED, false)
        set(wasDbCleared) = prefs.edit().putBoolean(WAS_DB_CLEARED, wasDbCleared).apply()

    var keyboardHeight: Int
        get() = prefs.getInt(SOFT_KEYBOARD_HEIGHT, context.getDefaultKeyboardHeight())
        set(keyboardHeight) = prefs.edit().putInt(SOFT_KEYBOARD_HEIGHT, keyboardHeight).apply()

    var useRecycleBin: Boolean
        get() = prefs.getBoolean(USE_RECYCLE_BIN, false)
        set(useRecycleBin) = prefs.edit().putBoolean(USE_RECYCLE_BIN, useRecycleBin).apply()

    var lastRecycleBinCheck: Long
        get() = prefs.getLong(LAST_RECYCLE_BIN_CHECK, 0L)
        set(lastRecycleBinCheck) = prefs.edit().putLong(LAST_RECYCLE_BIN_CHECK, lastRecycleBinCheck)
            .apply()

    var isArchiveAvailable: Boolean
        get() = prefs.getBoolean(IS_ARCHIVE_AVAILABLE, true)
        set(isArchiveAvailable) = prefs.edit().putBoolean(IS_ARCHIVE_AVAILABLE, isArchiveAvailable)
            .apply()

    var customNotifications: Set<String>
        get() = prefs.getStringSet(CUSTOM_NOTIFICATIONS, HashSet<String>())!!
        set(customNotifications) = prefs.edit()
            .putStringSet(CUSTOM_NOTIFICATIONS, customNotifications).apply()

    fun addCustomNotificationsByThreadId(threadId: Long) {
        customNotifications = customNotifications.plus(threadId.toString())
    }

    fun removeCustomNotificationsByThreadId(threadId: Long) {
        customNotifications = customNotifications.minus(threadId.toString())
    }

    var lastBlockedKeywordExportPath: String
        get() = prefs.getString(LAST_BLOCKED_KEYWORD_EXPORT_PATH, "")!!
        set(lastBlockedNumbersExportPath) = prefs.edit()
            .putString(LAST_BLOCKED_KEYWORD_EXPORT_PATH, lastBlockedNumbersExportPath).apply()

    var keepConversationsArchived: Boolean
        get() = prefs.getBoolean(KEEP_CONVERSATIONS_ARCHIVED, false)
        set(keepConversationsArchived) = prefs.edit()
            .putBoolean(KEEP_CONVERSATIONS_ARCHIVED, keepConversationsArchived).apply()
}
