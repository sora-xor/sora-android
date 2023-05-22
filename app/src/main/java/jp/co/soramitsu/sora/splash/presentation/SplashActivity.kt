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
import javax.inject.Inject
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter
import jp.co.soramitsu.sora.databinding.ActivitySplashBinding

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private val splashViewModel: SplashViewModel by viewModels()

    private lateinit var viewBinding: ActivitySplashBinding

    @Inject
    lateinit var multiaccStarter: MultiaccountStarter

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
            multiaccStarter.startOnboardingFlow(this)
        }
        splashViewModel.showOnBoardingScreenViaInviteLink.observe(this) {
            multiaccStarter.startOnboardingFlowWithInviteLink(this)
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
