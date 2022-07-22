/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.splash.presentation

import android.animation.ValueAnimator
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_onboarding_api.OnboardingStarter
import jp.co.soramitsu.sora.databinding.ActivitySplashBinding
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private val splashViewModel: SplashViewModel by viewModels()

    private lateinit var viewBinding: ActivitySplashBinding

    @Inject
    lateinit var onbnbst: OnboardingStarter
    @Inject
    lateinit var mainStarter: MainStarter

    private var isFirstPartFinished = false
    private var isSecondPartStarted = false

    private val animatorUpdateListener = ValueAnimator.AnimatorUpdateListener {
        val progress = it.animatedFraction
        if (progress >= 0.8 && !isFirstPartFinished) {
            isFirstPartFinished = true
            viewBinding.animationView.pauseAnimation()
        }
        if (splashViewModel.runtimeInitiated.value == true && isFirstPartFinished && !isSecondPartStarted) {
            isSecondPartStarted = true
            viewBinding.animationView.resumeAnimation()
        }
        if (progress >= 0.89) {
            goNext()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivitySplashBinding.inflate(layoutInflater).also { viewBinding = it }.root)

        viewBinding.animationView.addAnimatorUpdateListener(animatorUpdateListener)

        splashViewModel.runtimeInitiated.observe(
            this
        ) {
            if (it && isFirstPartFinished && !isSecondPartStarted) {
                isSecondPartStarted = true
                viewBinding.animationView.resumeAnimation()
            }
        }

        splashViewModel.showMainScreen.observe(this) {
            mainStarter.start(this)
        }
        splashViewModel.showOnBoardingScreen.observe(this) {
            onbnbst.start(this, it)
        }
        splashViewModel.showOnBoardingScreenViaInviteLink.observe(this) {
            onbnbst.startWithInviteLink(this)
            finish()
        }
        splashViewModel.showMainScreenFromInviteLink.observe(this) {
            mainStarter.startWithInvite(this)
            finish()
        }
    }

    private fun goNext() {
        viewBinding.animationView.removeUpdateListener(animatorUpdateListener)
        splashViewModel.nextScreen()
    }
}
