/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import android.net.Uri
import android.os.Parcelable
import java.math.BigDecimal
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

fun List<Token>.getByIdOrEmpty(id: String): Token =
    this.find {
        it.id == id
    } ?: AssetHolder.emptyToken

fun Token.iconUri(): Uri = this.iconFile ?: DEFAULT_ICON_URI

val DEFAULT_ICON: Int = R.drawable.ic_token_default
val DEFAULT_ICON_URI = Uri.parse("file:///android_asset/ic_token_default.png")
