/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.passphrase

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter

class PassphraseViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val preloader: WithPreloader
) : BaseViewModel(), WithPreloader by preloader {

    val passphraseLiveData = MutableLiveData<String>()

    fun resetUser() {
        disposables.add(
            interactor.clearUser()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    router.restartApp()
                }, {
                    onError(it)
                })
        )
    }

    fun getPassphrase() {
        disposables.add(
            interactor.getMnemonic()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(preloadCompose())
                .subscribe({
                    passphraseLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }
}