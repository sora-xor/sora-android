/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.sora.splash.presentation

import android.animation.ValueAnimator
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.ext.getColorFromAttrs
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

        val currentPageColorTypedValue = getColorFromAttrs(R.attr.baseBackground)

        viewBinding.animationView.apply {
            addValueCallback(
                KeyPath(
                    LOTTIE_WILDCARD_GLOBSTAR,
                    WAVE_ANIMATION_RINGS_PATH,
                    LOTTIE_WILDCARD_GLOBSTAR,
                ),
                LottieProperty.COLOR_FILTER
            ) {
                PorterDuffColorFilter(
                    currentPageColorTypedValue.data,
                    PorterDuff.Mode.MULTIPLY
                )
            }

            addAnimatorUpdateListener(
                animatorUpdateListener
            )
        }

        splashViewModel.runtimeInitiated.observe(
            this
        ) {
            if (it && isFirstPartFinished && !isSecondPartStarted) {
                isSecondPartStarted = true
                viewBinding.animationView.resumeAnimation()
            }
        }

        splashViewModel.loadingTextVisiblity.observe(
            this
        ) {
            viewBinding.loadingDisclaimerTextView.visibility = View.VISIBLE
            viewBinding.loadingProgressBar.visibility = View.VISIBLE
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

    private companion object {
        const val LOTTIE_WILDCARD_GLOBSTAR = "*"
        const val WAVE_ANIMATION_RINGS_PATH = "Ellipse 1"
    }
}
