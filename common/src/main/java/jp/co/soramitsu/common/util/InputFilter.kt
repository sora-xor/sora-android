/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.lang.ref.WeakReference

class ByteSizeTextWatcher(
    var editText: EditText?,
    val validationErrorEvent: () -> Unit,
    private val maxSizeInBytes: Int = NAME_BYTE_LIMIT
) : TextWatcher {
    var currentText: String? = null

    override fun afterTextChanged(s: Editable?) {
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        editText?.let {
            currentText = it.text.toString()
        }
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        editText?.let { editText ->
            if (s.toString().toByteArray().size > maxSizeInBytes) {
                validationErrorEvent()
                val newString = currentText
                var selection = editText.selectionStart - 1
                editText.setText(newString)

                selection = when {
                    selection < 0 -> {
                        0
                    }
                    selection > editText.text.length -> {
                        editText.text.length
                    }
                    else -> {
                        selection
                    }
                }

                editText.setSelection(selection)
            }
        }
    }

    fun destroy() {
        editText?.removeTextChangedListener(this)
        editText = null
    }
}

private const val NAME_BYTE_LIMIT = 32

fun nameByteSizeTextWatcher(editText: EditText, validationErrorEvent: () -> Unit) =
    ByteSizeTextWatcher(editText, validationErrorEvent)

fun LifecycleOwner.bindTextWatcher(textWatcher: ByteSizeTextWatcher) {
    lifecycle.addObserver(TextWatcherHolder(WeakReference(textWatcher)))
}

private class TextWatcherHolder(private val editText: WeakReference<ByteSizeTextWatcher>) :
    LifecycleEventObserver {

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            editText.get()?.destroy()
        }
    }
}
