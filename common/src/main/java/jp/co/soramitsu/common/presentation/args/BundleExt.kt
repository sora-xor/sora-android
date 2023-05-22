/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.args

import android.os.Bundle
import android.os.Parcelable
import java.io.Serializable
import jp.co.soramitsu.common.util.BuildUtils

const val BUNDLE_KEY = "BUNDLE_KEY"

@Suppress("DEPRECATION")
inline fun <reified T : Serializable> Bundle.getSerializableKey(key: String): T? {
    return if (BuildUtils.sdkAtLeast(33)) {
        getSerializable(key, T::class.java)
    } else {
        getSerializable(key) as? T
    }
}

@Suppress("DEPRECATION")
inline fun <reified T : Parcelable> Bundle.getParcelableKey(key: String): T? {
    return if (BuildUtils.sdkAtLeast(33)) {
        getParcelable(key, T::class.java)
    } else {
        getParcelable(key) as? T
    }
}

fun Bundle.requireString(key: String): String =
    requireNotNull(this.getString(key)) { "Argument with key $key is null" }

private const val ADDRESSES_KEY = "ADDRESSES"
var Bundle.addresses: List<String>
    get() = this.getStringArrayList(ADDRESSES_KEY) ?: emptyList()
    set(value) = this.putStringArrayList(
        ADDRESSES_KEY,
        value.toMutableList() as java.util.ArrayList<String>
    )

private const val ADDRESS_KEY = "ADDRESS"
var Bundle.address: String
    get() = this.requireString(ADDRESS_KEY)
    set(value) = this.putString(ADDRESS_KEY, value)

var Bundle.addressOrEmpty: String
    get() = this.getString(ADDRESS_KEY, "")
    set(value) = this.putString(ADDRESS_KEY, value)

private const val TRANSACTION_HASH_KEY = "TRANSACTION_HASH_KEY"
var Bundle.txHash: String
    get() = this.requireString(TRANSACTION_HASH_KEY)
    set(value) = this.putString(TRANSACTION_HASH_KEY, value)
