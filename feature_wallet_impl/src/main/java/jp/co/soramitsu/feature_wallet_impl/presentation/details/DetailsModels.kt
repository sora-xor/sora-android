/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.details

import android.os.Parcelable
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionLiquidityType
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionTransferType
import kotlinx.parcelize.Parcelize

@Parcelize
data class TransferDetailsModel(
    val transferType: TransactionTransferType,
    val amount1: String,
    val amount2: String,
    val date: String,
    val time: String,
    @DrawableRes val statusIcon: Int,
    @AttrRes val statusIconTintAttr: Int,
    @DrawableRes val tokenIcon: Int,
    val tokenName: String,
    val status: String,
    val txHash: String,
    @DrawableRes val txHashIcon: Int,
    val blockHash: String,
    @DrawableRes val blockHashIcon: Int,
    val from: String,
    val to: String,
    val fee: String,
    val statusText: String,
) : Parcelable

@Parcelize
data class LiquidityDetailsModel(
    val liquidityType: TransactionLiquidityType,
    @DrawableRes val statusIcon: Int,
    @DrawableRes val token1Icon: Int,
    @DrawableRes val token2Icon: Int,
    val status: TransactionStatus,
    val statusText: String,
    val statusDescription: String,
    val txHash: String,
    val fromAccount: String,
    val networkFee: String,
    val date: String,
    val time: String,
    val token1Name: String,
    val token1Amount: String,
    val token2Name: String,
    val token2Amount: String,
) : Parcelable

@Parcelize
data class SwapDetailsModel(
    val description: String,
    val date: String,
    val time: String,
    @DrawableRes val statusIcon: Int,
    val status: TransactionStatus,
    val statusText: String,
    val txHash: String,
    val fromAccount: String,
    val networkFee: String,
    val lpFee: String,
    val market: String,
    val sentAmount: String,
    @DrawableRes val receivedIcon: Int,
    val receivedTokenName: String,
    val amount1Full: String,
) : Parcelable
