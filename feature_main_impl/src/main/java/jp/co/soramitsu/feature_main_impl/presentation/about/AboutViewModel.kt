/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.about

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val _sourceTitleLiveData = MutableLiveData<String>()
    val sourceTitleLiveData: LiveData<String> = _sourceTitleLiveData

    val openSendEmailEvent = SingleLiveEvent<String>()

    private val _showBrowserLiveData = SingleLiveEvent<String>()
    val showBrowserLiveData: LiveData<String> = _showBrowserLiveData

    fun backPressed() {
        router.popBackStack()
    }

    fun openSourceClicked() {
        _showBrowserLiveData.value = OptionsProvider.sourceLink
    }

    fun websiteClicked() {
        _showBrowserLiveData.value = OptionsProvider.website
    }

    fun telegramClicked() {
        _showBrowserLiveData.value = OptionsProvider.telegramLink
    }

    fun telegramAnnouncementsClicked() {
        _showBrowserLiveData.value = OptionsProvider.telegramAnnouncementsLink
    }

    fun telegramAskSupportClicked() {
        _showBrowserLiveData.value = OptionsProvider.telegramHappinessLink
    }

    fun termsClicked() {
        router.showTerms()
    }

    fun privacyClicked() {
        router.showPrivacy()
    }

    fun getAppVersion() {
        viewModelScope.launch {
            val appVersion = interactor.getAppVersion()
            _sourceTitleLiveData.value =
                "${resourceManager.getString(R.string.common_app_version)} $appVersion"
        }
    }

    fun contactsClicked() {
        openSendEmailEvent.value = OptionsProvider.email
    }

    fun twitterClicked() {
        _showBrowserLiveData.value = OptionsProvider.twitterLink
    }

    fun youtubeClicked() {
        _showBrowserLiveData.value = OptionsProvider.youtubeLink
    }

    fun instagramClicked() {
        _showBrowserLiveData.value = OptionsProvider.instagramLink
    }

    fun mediumClicked() {
        _showBrowserLiveData.value = OptionsProvider.mediumLink
    }

    fun wikiClicked() {
        _showBrowserLiveData.value = OptionsProvider.wikiLink
    }
}
