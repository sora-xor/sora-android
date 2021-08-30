package jp.co.soramitsu.feature_wallet_impl.presentation.details

import android.os.Parcelable
import androidx.annotation.DrawableRes
import kotlinx.parcelize.Parcelize

@Parcelize
data class TransferDetailsModel(
    val amount1: String,
    val amount2: String,
    val date: String,
    @DrawableRes val statusIcon: Int,
    val status: String,
    val txHash: String,
    val blockHash: String,
    val from: String,
    val to: String,
    val fee: String,
) : Parcelable

@Parcelize
data class SwapDetailsModel(
    val amount1: String,
    val description: String,
    val date: String,
    @DrawableRes val statusIcon: Int,
    val status: String,
    val txHash: String,
    val fromAccount: String,
    val networkFee: String,
    val market: String,
    val amountSwapped: String,
    @DrawableRes val receivedIcon: Int,
    val toSymbol: String,
    val amount1Full: String,
) : Parcelable
