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

package jp.co.soramitsu.common.presentation.args

import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat
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

inline fun <reified T : Parcelable> Bundle.getParcelableKey(key: String): T? {
    return BundleCompat.getParcelable(this, key, T::class.java)
}

fun Bundle.requireString(key: String): String =
    requireNotNull(this.getString(key)) { "Argument with key $key is null" }

fun Bundle.requireBoolean(key: String): Boolean =
    requireNotNull(this.getBoolean(key)) { "Argument with key $key is null" }

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
