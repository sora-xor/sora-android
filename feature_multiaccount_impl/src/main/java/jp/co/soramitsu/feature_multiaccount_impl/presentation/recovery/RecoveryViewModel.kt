/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.recovery

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecoveryViewModel @Inject constructor(
    private val interactor: MultiaccountInteractor,
    private val router: MultiaccountRouter,
    private val resourceManager: ResourceManager,
    private val progress: WithProgress
) : BaseViewModel(), WithProgress by progress {

    @Suppress("UNCHECKED_CAST")
    companion object {
        const val MNEMONIC_INPUT_LENGTH = 150
    }

    private var recoverSoraAccountMethod = interactor::recoverSoraAccountFromMnemonic
    private var isValidMethod = interactor::isMnemonicValid
    private val _mnemonicInputLengthLiveData = SingleLiveEvent<Int>()
    val mnemonicInputLengthLiveData: LiveData<Int> = _mnemonicInputLengthLiveData

    private val _nextButtonEnabledLiveData = MutableLiveData<Boolean>()
    val nextButtonEnabledLiveData: LiveData<Boolean> = _nextButtonEnabledLiveData

    private var sourceTypeSelected = SourceType.PASSPHRASE

    private val _sourceTypeAndHintLiveData = MutableLiveData<Pair<String, String>>()
    val sourceTypeAndHintLiveData: LiveData<Pair<String, String>> = _sourceTypeAndHintLiveData

    private val _showSourceTypeDialog = SingleLiveEvent<SourceType>()
    val showSourceTypeDialog: LiveData<SourceType> = _showSourceTypeDialog

    private val _showMainScreen = SingleLiveEvent<Boolean>()
    val showMainScreen: LiveData<Boolean> = _showMainScreen

    private var errorMessageCode = ResponseCode.MNEMONIC_IS_NOT_VALID

    init {
        _mnemonicInputLengthLiveData.value = MNEMONIC_INPUT_LENGTH
    }

    fun btnNextClick(input: String, accountName: String) {
        viewModelScope.launch {
            progress.showProgress()
            try {
                val valid = isValidMethod(input)

                if (valid) {
                    val soraAccount = recoverSoraAccountMethod(input, accountName)
                    interactor.continueRecoverFlow(soraAccount)
                    val multiAccount = interactor.isMultiAccount()
                    _showMainScreen.value = multiAccount
                } else {
                    throw SoraException.businessError(errorMessageCode)
                }
            } catch (t: Throwable) {
                onError(t)
            } finally {
                progress.hideProgress()
            }
        }
    }

    fun onInputChanged(mnemonic: String) {
        _nextButtonEnabledLiveData.value = mnemonic.isNotEmpty()
    }

    fun showTermsScreen(navController: NavController) {
        router.showTermsScreen(navController)
    }

    fun showPrivacyScreen(navController: NavController) {
        router.showPrivacyScreen(navController)
    }

    fun sourceTypeClicked() {
        _showSourceTypeDialog.value = sourceTypeSelected
    }

    fun sourceTypeSelected(sourceType: SourceType) {
        sourceTypeSelected = sourceType

        _sourceTypeAndHintLiveData.value = when (sourceType) {
            SourceType.PASSPHRASE -> {
                isValidMethod = interactor::isMnemonicValid
                recoverSoraAccountMethod = interactor::recoverSoraAccountFromMnemonic
                errorMessageCode = ResponseCode.MNEMONIC_IS_NOT_VALID
                Pair(
                    resourceManager.getString(R.string.common_passphrase_title),
                    resourceManager.getString(R.string.recovery_mnemonic_passphrase)
                )
            }
            SourceType.RAW_SEED -> {
                isValidMethod = interactor::isRawSeedValid
                recoverSoraAccountMethod = interactor::recoverSoraAccountFromRawSeed
                errorMessageCode = ResponseCode.RAW_SEED_IS_NOT_VALID
                Pair(
                    resourceManager.getString(R.string.common_raw_seed),
                    resourceManager.getString(R.string.recovery_input_raw_seed_hint)
                )
            }
            SourceType.JSON -> {
                isValidMethod = interactor::isMnemonicValid
                recoverSoraAccountMethod = interactor::recoverSoraAccountFromMnemonic
                errorMessageCode = ResponseCode.MNEMONIC_IS_NOT_VALID
                Pair(
                    resourceManager.getString(R.string.common_passphrase_title),
                    resourceManager.getString(R.string.recovery_mnemonic_passphrase)
                )
            }
        }
    }
}
