package org.fossify.messages.helpers

import android.content.Context
import org.fossify.messages.extensions.config

object ReceiverUtils {

    fun containsPersonalKeyword(context: Context, body: String): Boolean {
        return context.config.personalKeywords.any { keyword ->
            keyword.isNotBlank() && body.contains(keyword, ignoreCase = true)
        }
    }

    fun isMessageFilteredOut(context: Context, body: String): Boolean {
        for (blockedKeyword in context.config.blockedKeywords) {
            if (body.contains(blockedKeyword, ignoreCase = true)) {
                return true
            }
        }

        return false
    }
}
