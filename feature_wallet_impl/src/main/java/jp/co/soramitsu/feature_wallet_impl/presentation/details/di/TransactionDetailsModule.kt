/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.details.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.details.TransactionDetailsViewModel
import java.math.BigDecimal
import javax.inject.Named

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class TransactionDetailsModule {

    @Provides
    @IntoMap
    @ViewModelKey(TransactionDetailsViewModel::class)
    fun provideViewModel(
        walletInteractor: WalletInteractor,
        walletRouter: WalletRouter,
        resourceManager: ResourceManager,
        numbersFormatter: NumbersFormatter,
        textFormatter: TextFormatter,
        dateTimeFormatter: DateTimeFormatter,
        @Named("assetId") assetId: String,
        @Named("myAccountId") myAccountId: String,
        @Named("peerId") peerId: String,
        @Named("peerFullName") peerFullName: String,
        transactionType: Transaction.Type,
        @Named("soranetTransactionId") soranetTransactionId: String,
        @Named("ethTransactionId") ethTransactionId: String,
        @Named("status") status: String,
        date: Long,
        @Named("amount") amount: BigDecimal,
        @Named("totalAmount") totalAmount: BigDecimal,
        @Named("transactionFee") transactionFee: BigDecimal,
        @Named("minerFee") minerFee: BigDecimal,
        @Named("description") description: String,
        clipboardManager: ClipboardManager
    ): ViewModel {
        return TransactionDetailsViewModel(
            walletInteractor,
            walletRouter,
            resourceManager,
            numbersFormatter,
            textFormatter,
            dateTimeFormatter,
            myAccountId,
            assetId,
            peerId,
            peerFullName,
            transactionType,
            soranetTransactionId,
            ethTransactionId,
            status,
            date,
            amount,
            totalAmount,
            transactionFee,
            minerFee,
            description,
            clipboardManager
        )
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): TransactionDetailsViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(TransactionDetailsViewModel::class.java)
    }
}