/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.splash.presentation

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.sora.R
import jp.co.soramitsu.sora.databinding.ActivitySplashBinding
import jp.co.soramitsu.sora.di.app_feature.AppFeatureComponent
import jp.co.soramitsu.sora.splash.domain.SplashRouter
import javax.inject.Inject

class SplashActivity : AppCompatActivity(), SplashRouter {

    companion object {
        const val SPLASH_ANIMATION_START_DELAY_1 = 300L
        const val SPLASH_ANIMATION_START_DELAY_2 = 600L
    }

    @Inject
    lateinit var splashViewModel: SplashViewModel

    private lateinit var viewBinding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inject()
        setContentView(ActivitySplashBinding.inflate(layoutInflater).also { viewBinding = it }.root)
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

    private fun doSplashAnimation(block: () -> Unit) {
        startAnimation(R.id.transitionFirst, SPLASH_ANIMATION_START_DELAY_1) {
            startAnimation(R.id.transitionSecond, SPLASH_ANIMATION_START_DELAY_2) {
                block.invoke()
                overridePendingTransition(R.anim.start, R.anim.finish)
            }
        }
    }

    private fun startAnimation(transition: Int, delay: Long, doAfter: () -> Unit) {
        viewBinding.splashContainer.setTransition(transition)
        viewBinding.splashContainer.setTransitionListener(object : MotionLayout.TransitionListener {
            override fun onTransitionCompleted(p0: MotionLayout?, p1: Int) {
                doAfter.invoke()
            }

            override fun onTransitionStarted(p0: MotionLayout?, p1: Int, p2: Int) {}

            override fun onTransitionChange(p0: MotionLayout?, p1: Int, p2: Int, p3: Float) {}

            override fun onTransitionTrigger(p0: MotionLayout?, p1: Int, p2: Boolean, p3: Float) {}
        })
        Handler().postDelayed(
            {
                viewBinding.splashContainer.transitionToEnd()
            },
            delay
        )
    }

    override fun showOnBoardingScreen(onBoardingState: OnboardingState) {
        doSplashAnimation {
            FeatureUtils.getFeature<OnboardingFeatureApi>(
                application,
                OnboardingFeatureApi::class.java
            )
                .provideOnboardingStarter()
                .start(this, onBoardingState)
        }
    }

    override fun showOnBoardingScreenViaInviteLink() {
        doSplashAnimation {
            FeatureUtils.getFeature<OnboardingFeatureApi>(
                application,
                OnboardingFeatureApi::class.java
            )
                .provideOnboardingStarter()
                .startWithInviteLink(this)
            finish()
        }
    }

    override fun showMainScreen() {
        doSplashAnimation {
            FeatureUtils.getFeature<MainFeatureApi>(application, MainFeatureApi::class.java)
                .provideMainStarter()
                .start(this)
        }
    }

    override fun showMainScreenFromInviteLink() {
        doSplashAnimation {
            FeatureUtils.getFeature<MainFeatureApi>(application, MainFeatureApi::class.java)
                .provideMainStarter()
                .startWithInvite(this)
            finish()
        }
    }
}
