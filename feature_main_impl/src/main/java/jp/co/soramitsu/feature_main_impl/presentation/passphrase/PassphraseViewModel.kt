/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.passphrase

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import kotlinx.coroutines.launch

class PassphraseViewModel(
    private val interactor: MainInteractor,
    private val preloader: WithPreloader
) : BaseViewModel(), WithPreloader by preloader {

    private var mnemonic: String? = null

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
                mnemonic = interactor.getMnemonic().also {
                    _mnemonicWords.value = it.split(" ")
                }
            }
        }
    }
}
