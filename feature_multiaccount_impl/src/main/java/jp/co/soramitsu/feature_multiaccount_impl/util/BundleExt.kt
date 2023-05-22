/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.util

import android.os.Bundle
import jp.co.soramitsu.common.presentation.args.requireString
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.ExportProtectionViewModel

private const val TYPE_KEY = "TYPE"
var Bundle.type: ExportProtectionViewModel.Type
    get() = ExportProtectionViewModel.Type.valueOf(this.requireString(TYPE_KEY))
    set(value) = this.putString(TYPE_KEY, value.toString())
