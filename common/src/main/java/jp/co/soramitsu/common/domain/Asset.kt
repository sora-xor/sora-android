/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import android.os.Parcelable
import androidx.annotation.DrawableRes
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
data class Asset(
    val token: Token,
    val isDisplaying: Boolean,
    val position: Int,
    var balance: AssetBalance,
) : Parcelable

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

@Parcelize
data class Token(
    val id: String,
    val name: String,
    val symbol: String,
    val precision: Int,
    val isHidable: Boolean,
    @DrawableRes val icon: Int,
) : Parcelable

fun List<Token>.getByIdOrEmpty(id: String): Token =
    this.find {
        it.id == id
    } ?: AssetHolder.emptyToken
