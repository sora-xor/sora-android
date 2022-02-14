/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.about

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import kotlinx.coroutines.launch

class AboutViewModel(
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
        _showBrowserLiveData.value = BuildConfig.SOURCE_LINK
    }

    fun websiteClicked() {
        _showBrowserLiveData.value = BuildConfig.WEBSITE
    }

    fun telegramClicked() {
        _showBrowserLiveData.value = BuildConfig.TELEGRAM_LINK
    }

    fun telegramAnnouncementsClicked() {
        _showBrowserLiveData.value = BuildConfig.TELEGRAM_ANNOUNCEMENTS_LINK
    }

    fun telegramAskSupportClicked() {
        _showBrowserLiveData.value = BuildConfig.TELEGRAM_HAPPINESS_LINK
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
        openSendEmailEvent.value = BuildConfig.EMAIL
    }

    fun twitterClicked() {
        _showBrowserLiveData.value = BuildConfig.TWITTER_LINK
    }

    fun youtubeClicked() {
        _showBrowserLiveData.value = BuildConfig.YOUTUBE_LINK
    }

    fun instagramClicked() {
        _showBrowserLiveData.value = BuildConfig.INSTAGRAM_LINK
    }

    fun mediumClicked() {
        _showBrowserLiveData.value = BuildConfig.MEDIUM_LINK
    }

    fun wikiClicked() {
        _showBrowserLiveData.value = BuildConfig.WIKI_LINK
    }
}
