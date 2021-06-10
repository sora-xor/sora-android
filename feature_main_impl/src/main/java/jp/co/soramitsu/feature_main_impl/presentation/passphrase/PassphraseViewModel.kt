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

class PassphraseViewModel(
    private val interactor: MainInteractor,
    private val preloader: WithPreloader
) : BaseViewModel(), WithPreloader by preloader {

    val passphraseLiveData = MutableLiveData<String>()

    fun getPassphrase() {
        disposables.add(
            interactor.getMnemonic()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(preloadCompose())
                .subscribe(
                    {
                        passphraseLiveData.value = it
                    },
                    {
                        it.printStackTrace()
                    }
                )
        )
    }
}
