/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter
import kotlinx.coroutines.launch

class MnemonicViewModel @AssistedInject constructor(
    private val interactor: MultiaccountInteractor,
    private val router: MultiaccountRouter,
    @Assisted private val accountName: String
) : BaseViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            accountName: String
        ): MnemonicViewModel
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun provideFactory(
            factory: Factory,
            accountName: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return factory.create(accountName) as T
            }
        }
    }

    private var mnemonic: String? = null
    private var soraAccount: SoraAccount? = null

    private val _mnemonicWords = MutableLiveData<List<String>>()
    val mnemonicWords: LiveData<List<String>> = _mnemonicWords

    private val _mnemonicShare = SingleLiveEvent<String>()
    val mnemonicShare: LiveData<String> = _mnemonicShare

    fun onShareClick() = mnemonic?.let {
        _mnemonicShare.value = it
    }

    fun getPassphrase() {
        viewModelScope.launch {
            tryCatch {
                soraAccount = interactor.generateUserCredentials(accountName)
                mnemonic = interactor.getMnemonic(soraAccount).also {
                    _mnemonicWords.value = it.split(" ")
                }
            }
        }
    }

    fun btnNextClicked(navController: NavController) {
        soraAccount?.let {
            router.showMnemonicConfirmation(navController, it)
        }
    }
}
