/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic_confirmation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.ext.map
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter
import kotlinx.coroutines.launch

class MnemonicConfirmationViewModel(
    private val interactor: MultiaccountInteractor,
    private val deviceVibrator: DeviceVibrator,
    private val router: MultiaccountRouter,
    private val preloader: WithPreloader
) : BaseViewModel(), WithPreloader by preloader {

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

    private val _showMainScreen = SingleLiveEvent<Unit>()
    val showMainScreen: LiveData<Unit> = _showMainScreen

    init {
        viewModelScope.launch {
            mnemonicList = interactor.getMnemonic().run {
                split(" ").toList()
            }
            _shuffledMnemonicLiveData.value = mnemonicList.shuffled()
        }
    }

    fun homeButtonClicked() {
        router.onBackButtonPressed()
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
            interactor.saveRegistrationStateFinished()
            _showMainScreen.call()
        }
    }

    fun matchingErrorAnimationCompleted() {
        reset()
    }

    fun skipClicked() {
        proceed()
    }
}
