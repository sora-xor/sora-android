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

package jp.co.soramitsu.common.domain

import android.os.Parcelable
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.equalTo
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.NumbersFormatter
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class Asset(
    val token: Token,
    val favorite: Boolean,
    val position: Int,
    var balance: AssetBalance,
    val visibility: Boolean,
) : Parcelable {

    @IgnoredOnParcel
    val fiat: Double? by lazy {
        token.calcFiat(balance.transferable)
    }

    fun printBalance(
        nf: NumbersFormatter,
        withSymbol: Boolean = true,
        precision: Int = this.token.precision,
    ): String = if (withSymbol) token.printBalance(
        balance.transferable,
        nf,
        precision
    ) else nf.formatBigDecimal(balance.transferable, precision)
}

@Parcelize
data class AssetBalance(
    val transferable: BigDecimal,
    val reserved: BigDecimal,
    val miscFrozen: BigDecimal,
    val feeFrozen: BigDecimal,
    val bonded: BigDecimal,
    val redeemable: BigDecimal,
    val unbonding: BigDecimal,
    val total: BigDecimal,
) : Parcelable

fun createAssetBalance(b: BigDecimal = BigDecimal.ZERO) = AssetBalance(b, b, b, b, b, b, b, b)

/** The free balance on the chain: [AssetBalance.total] is free plus reserved plus bonded. */
val AssetBalance.free: BigDecimal
    get() = (total - reserved - bonded).max(BigDecimal.ZERO)

/**
 * The part of the free balance that cannot be sent now, for example XOR locked by staking,
 * voting or vesting. Zero for every asset without locks.
 */
val AssetBalance.lockedFree: BigDecimal
    get() = (free - transferable).max(BigDecimal.ZERO)

/**
 * Everything the account holds of this asset and cannot send now: reserved, bonded and locked.
 * Together with [AssetBalance.transferable] it is the [AssetBalance.total].
 */
val AssetBalance.frozenTotal: BigDecimal
    get() = (total - transferable).max(BigDecimal.ZERO)

/**
 * The largest network fee the chain lets this balance pay now. The fee is withdrawn before the
 * call runs and may not take the free balance below what is held back from fees
 * ([AssetBalance.feeFrozen]), which can be more than what is held back from transfers.
 */
val AssetBalance.payableAsFee: BigDecimal
    get() = (free - feeFrozen.max(BigDecimal.ZERO)).max(BigDecimal.ZERO)

typealias TokenPrecision = Pair<String, Int>

@Parcelize
data class Token(
    val id: String,
    val name: String,
    val symbol: String,
    val precision: Int,
    val isHidable: Boolean,
    val iconFile: String?,
    val fiatPrice: Double?,
    val fiatPriceChange: Double?,
    val fiatSymbol: String?,
) : Parcelable {

    fun printBalance(
        balance: BigDecimal,
        nf: NumbersFormatter,
        precision: Int = this.precision
    ): String =
        String.format("%s %s", nf.formatBigDecimal(balance, precision), symbol)

    fun printBalanceWithConstrainedLength(
        balance: BigDecimal,
        nf: NumbersFormatter,
        length: Int = 8
    ): String {
        val integerLength = balance.toBigInteger().toString().length
        var newPrecision = length - integerLength
        if (newPrecision <= 0) {
            newPrecision = 1
        }

        return printBalance(balance, nf, newPrecision)
    }
}

fun compareByTransferable(old: Asset?, new: Asset?): Boolean = if (old == null && new == null) {
    true
} else if (old == null || new == null) {
    false
} else {
    old.token.id == new.token.id && old.balance.transferable.equalTo(new.balance.transferable)
}

fun List<Token>.getByIdOrEmpty(id: String): Token =
    this.find {
        it.id == id
    } ?: AssetHolder.emptyToken

fun Token.iconUri(): String = this.iconFile ?: DEFAULT_ICON_URI

fun Token.isMatchFilter(filter: String): Boolean =
    name.lowercase().contains(filter.lowercase()) ||
        symbol.lowercase().contains(filter.lowercase()) ||
        id.lowercase().contains(filter.lowercase())

val DEFAULT_ICON: Int = R.drawable.ic_token_default
val DEFAULT_ICON_URI = "file:///android_asset/ic_token_default.png"
