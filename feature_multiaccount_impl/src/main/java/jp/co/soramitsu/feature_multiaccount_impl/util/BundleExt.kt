/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.util

import android.os.Bundle
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.ExportProtectionViewModel

private const val ADDRESS_KEY = "ADDRESS"
var Bundle.address: String
    get() = this.getString(ADDRESS_KEY) ?: throw IllegalArgumentException("Argument with key $ADDRESS_KEY is null")
    set(value) = this.putString(ADDRESS_KEY, value)

private const val ADDRESSES_KEY = "ADDRESSES"
var Bundle.addresses: List<String>
    get() = this.getStringArrayList(ADDRESSES_KEY) ?: emptyList()
    set(value) = this.putStringArrayList(ADDRESSES_KEY, value.toMutableList() as java.util.ArrayList<String>)

private const val TYPE_KEY = "TYPE"
var Bundle.type: ExportProtectionViewModel.Type
    get() = ExportProtectionViewModel.Type.valueOf(
        this.getString(TYPE_KEY)
            ?: throw IllegalArgumentException("Argument with key $TYPE_KEY is null")
    )
    set(value) = this.putString(TYPE_KEY, value.toString())
