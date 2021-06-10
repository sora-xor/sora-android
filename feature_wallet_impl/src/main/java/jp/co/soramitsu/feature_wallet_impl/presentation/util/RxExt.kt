/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.util

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import io.reactivex.Observable
import jp.co.soramitsu.feature_wallet_impl.presentation.view.CurrencyEditText

fun EditText.observeTextChanges(): Observable<String> {
    return Observable.create { emitter ->
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!emitter.isDisposed) emitter.onNext(s.toString())
            }
        })
    }
}

fun CurrencyEditText.observeTextChanges(): Observable<String> {
    return Observable.create { emitter ->
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!emitter.isDisposed) {
                    val valueStr = if (text.isNullOrEmpty() || text!!.startsWith(".")) {
                        ""
                    } else {
                        getBigDecimal().toString()
                    }
                    val pointIndex = s?.indexOf(".") ?: -1
                    val length = s?.length ?: 0
                    if (pointIndex == -1 || length - pointIndex <= 3) {
                        emitter.onNext(valueStr)
                    }
                }
            }
        })
    }
}
