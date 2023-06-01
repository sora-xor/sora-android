/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
