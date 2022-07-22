/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.userverification

import androidx.lifecycle.LiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import javax.inject.Inject

@HiltViewModel
class UserVerificationViewModel @Inject constructor(
    private val router: MainRouter,
    private val interactor: PinCodeInteractor
) : BaseViewModel() {

    private val _checkInviteLiveData = SingleLiveEvent<Unit>()
    val checkInviteLiveData: LiveData<Unit> = _checkInviteLiveData

    private val _restartApplicationLiveData = SingleLiveEvent<Unit>()
    val restartApplicationLiveData: LiveData<Unit> = _restartApplicationLiveData

    fun checkUser() {
        _checkInviteLiveData.trigger()
        router.popBackStack()
//        disposables.add(
//            interactor.runCheckUserFlow()
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(
//                    {
//                        if (it.supported) {
//                            _checkInviteLiveData.value = Event(Unit)
//                            router.popBackStack()
//                        } else {
//                            router.showUnsupportedScreen(it.downloadUrl)
//                        }
//                    },
//                    {
//                        if (it is SoraException && it.kind == SoraException.Kind.BUSINESS && ResponseCode.DID_NOT_FOUND == it.errorResponseCode) {
//                            resetUser()
//                        } else {
//                            onError(it)
//                        }
//                    }
//                )
//        )
    }

//    private fun resetUser() {
//        disposables.add(
//            interactor.resetUser()
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(
//                    {
//                        _restartApplicationLiveData.value = Event(Unit)
//                    },
//                    {
//                        onError(it)
//                    }
//                )
//        )
//    }
}
