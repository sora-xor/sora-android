/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.recovery

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter

class RecoveryViewModel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter,
    private val progress: WithProgress
) : BaseViewModel(), WithProgress by progress {

    companion object {
        const val MNEMONIC_INPUT_LENGTH = 150
    }

    private val _mnemonicInputLengthLiveData = MutableLiveData(MNEMONIC_INPUT_LENGTH)
    val mnemonicInputLengthLiveData: LiveData<Int> = _mnemonicInputLengthLiveData

    private val _nextButtonEnabledLiveData = MutableLiveData<Boolean>()
    val nextButtonEnabledLiveData: LiveData<Boolean> = _nextButtonEnabledLiveData

    fun btnNextClick(mnemonic: String, accountName: String) {
        disposables.add(
            interactor.isMnemonicValid(mnemonic)
                .flatMapCompletable {
                    if (it) {
                        interactor.runRecoverFlow(mnemonic, accountName)
                    } else {
                        Completable.error(SoraException.businessError(ResponseCode.MNEMONIC_IS_NOT_VALID))
                    }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { progress.showProgress() }
                .subscribe(
                    {
                        progress.hideProgress()
                        router.showMainScreen()
                    },
                    {
                        progress.hideProgress()
                        onError(it)
                    }
                )
        )
    }

    fun backButtonClick() {
        router.onBackButtonPressed()
    }

    fun onInputChanged(mnemonic: String) {
        _nextButtonEnabledLiveData.value = mnemonic.isNotEmpty()
    }
}
