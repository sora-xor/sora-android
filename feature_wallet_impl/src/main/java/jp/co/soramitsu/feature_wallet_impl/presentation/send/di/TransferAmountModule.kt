/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.delegate.WithProgressImpl
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferAmountViewModel
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import java.math.BigDecimal
import javax.inject.Named

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class TransferAmountModule {

    @Provides
    fun provideProgress(): WithProgress {
        return WithProgressImpl()
    }

    @Provides
    @IntoMap
    @ViewModelKey(TransferAmountViewModel::class)
    fun provideViewModel(
        walletInteractor: WalletInteractor,
        etherInteractor: EthereumInteractor,
        router: WalletRouter,
        progress: WithProgress,
        numbersFormatter: NumbersFormatter,
        dateTimeFormatter: DateTimeFormatter,
        textFormatter: TextFormatter,
        resourceManager: ResourceManager,
        @Named("recipientId") recipientId: String,
        @Named("recipientFullName") recipientFullName: String,
        initialAmount: BigDecimal,
        transferType: TransferType
    ): ViewModel {
        return TransferAmountViewModel(walletInteractor, etherInteractor, router, progress, numbersFormatter, dateTimeFormatter, textFormatter, resourceManager,
            recipientId, recipientFullName, initialAmount, transferType)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): TransferAmountViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(TransferAmountViewModel::class.java)
    }
}