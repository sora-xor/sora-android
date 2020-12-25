/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.details.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_impl.presentation.details.TransactionDetailsFragment
import java.math.BigDecimal
import javax.inject.Named

@Subcomponent(
    modules = [
        TransactionDetailsModule::class
    ]
)
@ScreenScope
interface TransactionDetailsComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        @BindsInstance
        fun withMyAccountId(@Named("myAccountId") myAccountId: String): Builder

        @BindsInstance
        fun withPeerId(@Named("peerId") peerId: String): Builder

        @BindsInstance
        fun withPeerFullName(@Named("peerFullName") peerFullName: String): Builder

        @BindsInstance
        fun withSoranetTransactionId(@Named("soranetTransactionId") transactionId: String): Builder

        @BindsInstance
        fun withEthTransactionId(@Named("ethTransactionId") transactionId: String): Builder

        @BindsInstance
        fun withSecondEthTransactionId(@Named("secondEthTransactionId") transactionId: String): Builder

        @BindsInstance
        fun withStatus(@Named("status") status: Transaction.Status): Builder

        @BindsInstance
        fun withDetailedStatus(@Named("detailedStatus") status: Transaction.DetailedStatus): Builder

        @BindsInstance
        fun withAssetId(@Named("assetId") assetId: String): Builder

        @BindsInstance
        fun withTransactionType(transcationType: Transaction.Type): Builder

        @BindsInstance
        fun withDate(date: Long): Builder

        @BindsInstance
        fun withAmount(@Named("amount") amount: BigDecimal): Builder

        @BindsInstance
        fun withTotalAmount(@Named("totalAmount") totalAmount: BigDecimal): Builder

        @BindsInstance
        fun withTransactionFee(@Named("transactionFee") fee: BigDecimal): Builder

        @BindsInstance
        fun withMinerFee(@Named("minerFee") fee: BigDecimal): Builder

        @BindsInstance
        fun withDescription(@Named("description") description: String): Builder

        @BindsInstance
        fun withTransferType(@Named("transferType") transferType: TransferType): Builder

        fun build(): TransactionDetailsComponent
    }

    fun inject(transactionDetailsFragment: TransactionDetailsFragment)
}