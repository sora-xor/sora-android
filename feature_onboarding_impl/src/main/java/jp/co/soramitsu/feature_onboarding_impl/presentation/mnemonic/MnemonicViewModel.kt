/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter

class MnemonicViewModel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter,
    private val preloader: WithPreloader
) : BaseViewModel(), WithPreloader by preloader {

    private val _mnemonicLiveData = MutableLiveData<String>()
    val mnemonicLiveData: LiveData<String> = _mnemonicLiveData

    fun btnNextClicked() {
        router.showMainScreen()
    }

    fun getPassphrase() {
        disposables.add(
            interactor.getMnemonic()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(preloadCompose())
                .subscribe({
                    _mnemonicLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }
}