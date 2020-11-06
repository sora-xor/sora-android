package jp.co.soramitsu.common.resourses

import android.content.ClipData
import android.content.ClipboardManager

class ClipboardManager(
    private val clipboardManager: ClipboardManager
) {

    fun addToClipboard(label: String, text: String) {
        val clip = ClipData.newPlainText(label, text)
        clipboardManager.primaryClip = clip
    }
}