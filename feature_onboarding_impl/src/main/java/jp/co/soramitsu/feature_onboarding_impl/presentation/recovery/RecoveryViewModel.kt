/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.recovery

import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.mnemonic.MnemonicUtil
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter

class RecoveryViewModel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter,
    private val progress: WithProgress
) : BaseViewModel(), WithProgress by progress {

    fun btnNextClick(mnemonic: String) {
        val mnemonics = MnemonicUtil.splitToArray(mnemonic)
        if (mnemonics.size == 15) {
            if (MnemonicUtil.checkMnemonic(mnemonics)) {
                disposables.add(
                    interactor.runRecoverFlow(mnemonic)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnSubscribe { progress.showProgress() }
                        .subscribe({
                            progress.hideProgress()
                            router.showMainScreen()
                        }, {
                            progress.hideProgress()
                            onError(it)
                        })
                )
            } else {
                progress.hideProgress()
                onError(SoraException.businessError(ResponseCode.MNEMONIC_IS_NOT_VALID))
            }
        } else {
            progress.hideProgress()
            onError(SoraException.businessError(ResponseCode.MNEMONIC_LENGTH_ERROR))
        }
    }

    fun backButtonClick() {
        router.onBackButtonPressed()
    }
}