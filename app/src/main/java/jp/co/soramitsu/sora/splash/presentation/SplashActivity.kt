/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.splash.presentation

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import jp.co.soramitsu.common.util.OnboardingState
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.sora.di.app_feature.AppFeatureComponent
import jp.co.soramitsu.sora.splash.domain.SplashRouter
import javax.inject.Inject

class SplashActivity : AppCompatActivity(), SplashRouter {

    @Inject lateinit var splashViewModel: SplashViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inject()

        if (Intent.ACTION_VIEW == intent.action) {
            intent?.data?.lastPathSegment?.let {
                splashViewModel.handleDeepLink(it)
            } ?: splashViewModel.nextScreen()
        } else {
            splashViewModel.nextScreen()
        }
    }

    private fun inject() {
        FeatureUtils.getFeature<AppFeatureComponent>(this, AppFeatureComponent::class.java)
            .splashComponentBuilder()
            .withActivity(this)
            .withRouter(this)
            .build()
            .inject(this)
    }

    override fun showOnBoardingScreen(onBoardingState: OnboardingState) {
        val handler = Handler()
        handler.postDelayed({
            FeatureUtils.getFeature<OnboardingFeatureApi>(application, OnboardingFeatureApi::class.java)
                .provideOnboardingStarter()
                .start(this, onBoardingState)
        }, 2000)
    }

    override fun showOnBoardingScreenViaInviteLink() {
        val handler = Handler()
        handler.postDelayed({
            FeatureUtils.getFeature<OnboardingFeatureApi>(application, OnboardingFeatureApi::class.java)
                .provideOnboardingStarter()
                .startWithInviteLink(this)
            finish()
        }, 1000)
    }

    override fun showMainScreen() {
        val handler = Handler()
        handler.postDelayed({
            FeatureUtils.getFeature<MainFeatureApi>(application, MainFeatureApi::class.java)
                .provideMainStarter()
                .start(this)
        }, 1000)
    }

    override fun showMainScreenFromInviteLink() {
        val handler = Handler()
        handler.postDelayed({
            FeatureUtils.getFeature<MainFeatureApi>(application, MainFeatureApi::class.java)
                .provideMainStarter()
                .startWithInvite(this)
            finish()
        }, 1000)
    }
}