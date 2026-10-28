/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
