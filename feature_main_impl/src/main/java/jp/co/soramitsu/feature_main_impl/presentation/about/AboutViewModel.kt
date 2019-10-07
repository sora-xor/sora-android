/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.about

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter

class AboutViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    val appVersionLiveData = MutableLiveData<String>()
    val openSendEmailEvent = MutableLiveData<Event<String>>()

    fun backPressed() {
        router.popBackStackFragment()
    }

    fun opensourceClick() {
        router.showBrowser(resourceManager.getString(R.string.open_source_link))
    }

    fun termsClick() {
        router.showTermsFragment()
    }

    fun privacyClick() {
        router.showPrivacy()
    }

    fun init() {
        disposables.add(
            interactor.getAppVersion()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    appVersionLiveData.value = it
                }, {
                    logException(it)
                })
        )
    }

    fun contactsClicked() {
        openSendEmailEvent.value = Event(resourceManager.getString(R.string.sora_support_email))
    }
}