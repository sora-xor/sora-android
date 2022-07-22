/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.args

import android.os.Bundle
import jp.co.soramitsu.common.account.SoraAccount
import java.lang.IllegalArgumentException

private const val ACCOUNT_NAME_KEY = "ACCOUNT_NAME_KEY"
var Bundle.accountName: String
    get() = this.getString(ACCOUNT_NAME_KEY, "")
    set(value) = this.putString(ACCOUNT_NAME_KEY, value)

private const val SORA_ACCOUNT_KEY = "SORA_ACCOUNT_KEY"
var Bundle.soraAccount: SoraAccount
    get() = this.getParcelable(SORA_ACCOUNT_KEY)
        ?: throw IllegalArgumentException("Argument with key $SORA_ACCOUNT_KEY is null")
    set(value) = this.putParcelable(SORA_ACCOUNT_KEY, value)
