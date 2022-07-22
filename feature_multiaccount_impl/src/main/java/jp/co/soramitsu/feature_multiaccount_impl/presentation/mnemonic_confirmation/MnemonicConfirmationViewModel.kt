/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic_confirmation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.ext.map
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import kotlinx.coroutines.launch

class MnemonicConfirmationViewModel @AssistedInject constructor(
    private val interactor: MultiaccountInteractor,
    private val deviceVibrator: DeviceVibrator,
    private val withProgress: WithProgress,
    @Assisted private val soraAccount: SoraAccount
) : BaseViewModel(), WithProgress by withProgress {

    @AssistedFactory
    interface Factory {
        fun create(soraAccount: SoraAccount): MnemonicConfirmationViewModel
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun provideFactory(
            factory: Factory,
            soraAccount: SoraAccount
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return factory.create(soraAccount) as T
            }
        }
    }

    var mnemonicList = emptyList<String>()

    private val _shuffledMnemonicLiveData = MutableLiveData<List<String>>()
    val shuffledMnemonicLiveData: LiveData<List<String>> = _shuffledMnemonicLiveData

    private val confirmationMnemonicWords = MutableLiveData<List<String>>(emptyList())

    private val _resetConfirmationEvent = SingleLiveEvent<Unit>()
    val resetConfirmationEvent: LiveData<Unit> = _resetConfirmationEvent

    private val _removeLastWordFromConfirmationEvent = SingleLiveEvent<Unit>()
    val removeLastWordFromConfirmationEvent: LiveData<Unit> = _removeLastWordFromConfirmationEvent

    val nextButtonEnableLiveData: LiveData<Boolean> = confirmationMnemonicWords.map {
        _shuffledMnemonicLiveData.value?.let { shuffled ->
            shuffled.size == it.size
        } ?: false
    }

    private val _matchingMnemonicErrorAnimationEvent = SingleLiveEvent<Unit>()
    val matchingMnemonicErrorAnimationEvent: LiveData<Unit> = _matchingMnemonicErrorAnimationEvent

    private val _showMainScreen = SingleLiveEvent<Boolean>()
    val showMainScreen: LiveData<Boolean> = _showMainScreen

    init {
        viewModelScope.launch {
            mnemonicList = interactor.getMnemonic(soraAccount).run {
                split(" ").toList()
            }
            _shuffledMnemonicLiveData.value = mnemonicList.shuffled()
        }
    }

    fun resetConfirmationClicked() {
        reset()
    }

    private fun reset() {
        confirmationMnemonicWords.value = mutableListOf()
        _resetConfirmationEvent.trigger()
    }

    fun addWordToConfirmMnemonic(word: String) {
        confirmationMnemonicWords.value?.let {
            val wordList = mutableListOf<String>().apply {
                addAll(it)
                add(word)
            }
            confirmationMnemonicWords.value = wordList
        }
    }

    fun removeLastWordFromConfirmation() {
        confirmationMnemonicWords.value?.let {
            if (it.isEmpty()) {
                return
            }
            val wordList = mutableListOf<String>().apply {
                addAll(it.subList(0, it.size - 1))
            }
            confirmationMnemonicWords.value = wordList
        }

        _removeLastWordFromConfirmationEvent.trigger()
    }

    fun nextButtonClicked() {
        confirmationMnemonicWords.value?.let { enteredWords ->
            if (mnemonicList == enteredWords) {
                proceed()
            } else {
                deviceVibrator.makeShortVibration()
                _matchingMnemonicErrorAnimationEvent.trigger()
                onError(R.string.mnemonic_invalid)
            }
        }
    }

    private fun proceed() {
        viewModelScope.launch {
            showProgress()
            interactor.createUser(soraAccount)
            interactor.saveRegistrationStateFinished()
            val multiAccount = interactor.isMultiAccount()
            hideProgress()
            _showMainScreen.value = multiAccount
        }
    }

    fun matchingErrorAnimationCompleted() {
        reset()
    }

    fun skipClicked() {
        proceed()
    }
}
