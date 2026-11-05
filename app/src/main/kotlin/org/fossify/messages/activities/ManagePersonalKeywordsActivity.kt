package org.fossify.messages.activities

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import org.fossify.messages.R
import org.fossify.messages.extensions.config

class ManagePersonalKeywordsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showKeywords()
    }

    private fun showKeywords() {
        val keywords = config.personalKeywords.sorted()
        AlertDialog.Builder(this)
            .setTitle(R.string.personal_keywords)
            .setItems(keywords.toTypedArray()) { _, which ->
                editKeyword(keywords[which])
            }
            .setPositiveButton(R.string.add_personal_keyword) { _, _ -> editKeyword(null) }
            .setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun editKeyword(original: String?) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            setText(original.orEmpty())
            setSelection(text.length)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (original == null) R.string.add_personal_keyword else R.string.personal_keywords)
            .setView(input)
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                if (original != null) {
                    setNeutralButton(org.fossify.commons.R.string.delete) { _, _ ->
                        config.removePersonalKeyword(original)
                        showKeywords()
                    }
                }
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim()
                if (value.isEmpty()) {
                    input.error = "Enter a keyword"
                    return@setOnClickListener
                }
                if (original != null && original != value) config.removePersonalKeyword(original)
                config.addPersonalKeyword(value)
                dialog.dismiss()
                showKeywords()
            }
        }
        dialog.show()
    }
}
