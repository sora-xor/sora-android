/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.util

import android.os.Bundle
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction

private const val ADDRESSES_KEY = "ADDRESSES"
var Bundle.addresses: List<String>
    get() = this.getStringArrayList(ADDRESSES_KEY) ?: emptyList()
    set(value) = this.putStringArrayList(ADDRESSES_KEY, value.toMutableList() as java.util.ArrayList<String>)

private const val PIN_ACTION = "PIN_ACTION"
var Bundle.action: PinCodeAction
    get() = this.getSerializable(PIN_ACTION) as PinCodeAction
    set(value) = this.putSerializable(PIN_ACTION, value)
