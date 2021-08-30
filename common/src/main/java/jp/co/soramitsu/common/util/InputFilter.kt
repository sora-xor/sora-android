/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

class ByteSizeTextWatcher(var editText: EditText?, val validationErrorEvent: () -> Unit, val maxSizeInBytes: Int = NAME_BYTE_LIMIT) : TextWatcher {
    var currentText: String? = null

    override fun afterTextChanged(s: Editable?) {
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        currentText = editText!!.text.toString()
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        if (s.toString().toByteArray().size > maxSizeInBytes) {
            validationErrorEvent()
            val newString = currentText
            var selection = editText!!.selectionStart - 1
            editText!!.setText(newString)

            selection = when {
                selection < 0 -> {
                    0
                }
                selection > editText!!.text.length -> {
                    editText!!.text.length
                }
                else -> {
                    selection
                }
            }

            editText!!.setSelection(selection)
        }
    }

    fun destroy() {
        editText = null
    }
}

private const val NAME_BYTE_LIMIT = 32

fun nameByteSizeTextWatcher(editText: EditText, validationErrorEvent: () -> Unit) = ByteSizeTextWatcher(editText, validationErrorEvent)
