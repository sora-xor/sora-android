/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.util

import android.os.Bundle
import jp.co.soramitsu.common.presentation.args.getSerializableKey
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction

private const val PIN_ACTION = "PIN_ACTION"
var Bundle.action: PinCodeAction
    get() = requireNotNull(this.getSerializableKey(PIN_ACTION))
    set(value) = this.putSerializable(PIN_ACTION, value)
