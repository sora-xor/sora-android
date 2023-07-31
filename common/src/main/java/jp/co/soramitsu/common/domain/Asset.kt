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

package jp.co.soramitsu.common.domain

import android.net.Uri
import android.os.Parcelable
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.equalTo
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
    val unbonding: BigDecimal
) : Parcelable

fun createAssetBalance(b: BigDecimal = BigDecimal.ZERO) = AssetBalance(b, b, b, b, b, b, b)

@Parcelize
data class Token(
    val id: String,
    val name: String,
    val symbol: String,
    val precision: Int,
    val isHidable: Boolean,
    val iconFile: Uri?,
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

fun Token.iconUri(): Uri = this.iconFile ?: DEFAULT_ICON_URI

fun Token.isMatchFilter(filter: String): Boolean =
    name.lowercase().contains(filter.lowercase()) ||
        symbol.lowercase().contains(filter.lowercase()) ||
        id.lowercase().contains(filter.lowercase())

val DEFAULT_ICON: Int = R.drawable.ic_token_default
val DEFAULT_ICON_URI = Uri.parse("file:///android_asset/ic_token_default.png")
