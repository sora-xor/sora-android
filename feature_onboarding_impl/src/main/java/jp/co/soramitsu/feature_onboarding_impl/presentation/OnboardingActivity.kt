/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.Navigation
import dev.chrisbanes.insetter.Insetter
import dev.chrisbanes.insetter.windowInsetTypesOf
import jp.co.soramitsu.common.data.network.substrate.ConnectionManager
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.view.ToolbarActivity
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_multiaccount_api.di.MultiaccountFeatureApi
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.databinding.ActivityOnboardingBinding
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.version.UnsupportedVersionFragment
import javax.inject.Inject

class OnboardingActivity :
    ToolbarActivity<OnboardingViewModel, ActivityOnboardingBinding>(), OnboardingRouter {

    companion object {

        private const val KEY_ONBOARDING_STATE = "onboarding_state"
        const val ACTION_INVITE = "jp.co.soramitsu.feature_onboarding_impl.ACTION_INVITE"

        private const val SWITCH_ACCOUNT_MODE_KEY =
            "jp.co.soramitsu.feature_onboarding_impl.SWITCH_ACCOUNT_MODE_KEY"

        fun start(context: Context, state: OnboardingState) {
            val intent = Intent(context, OnboardingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(KEY_ONBOARDING_STATE, state)
            }
            val options = ActivityOptions.makeCustomAnimation(context, android.R.anim.fade_in, android.R.anim.fade_out)
            context.startActivity(intent, options.toBundle())
        }

        fun startWithInviteLink(context: Context) {
            val intent = Intent(context, OnboardingActivity::class.java).apply {
                action = ACTION_INVITE
            }
            val options = ActivityOptions.makeCustomAnimation(context, android.R.anim.fade_in, android.R.anim.fade_out)
            context.startActivity(intent, options.toBundle())
        }
    }

    @Inject
    lateinit var mainStarter: MainStarter

    @Inject
    lateinit var cm: ConnectionManager

    private lateinit var navController: NavController

    override fun layoutResource() = ActivityOnboardingBinding.inflate(layoutInflater)

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(this, OnboardingFeatureApi::class.java)
            .onboardingComponentBuilder()
            .withActivity(this)
            .build()
            .inject(this)
    }

    override fun initViews() {
        Insetter.builder()
            .paddingTop(windowInsetTypesOf(statusBars = true))
            .paddingBottom(windowInsetTypesOf(navigationBars = true))
            .applyToView(binding.flContainerOnboarding)
        initNavigation()
    }

    override fun subscribe(viewModel: OnboardingViewModel) {}

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        intent?.let {
            if (ACTION_INVITE == it.action) {
                viewModel.startedWithInviteAction()
            }
        }
    }

    private fun initNavigation() {
        navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        navController.setGraph(R.navigation.onboarding_nav_graph)
    }

    override fun showPersonalInfo() {
        FeatureUtils.getFeature<MultiaccountFeatureApi>(application, MultiaccountFeatureApi::class.java)
            .provideMultiaccountStarter()
            .startCreateAccount(navController)
    }

    override fun showRecovery() {
        FeatureUtils.getFeature<MultiaccountFeatureApi>(application, MultiaccountFeatureApi::class.java)
            .provideMultiaccountStarter()
            .startRecoveryAccount(navController)
    }

    override fun showBrowser(link: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(link)
        }
        startActivity(intent)
    }

    override fun showUnsupportedScreen(appUrl: String) {
        UnsupportedVersionFragment.newInstance(appUrl, navController)
    }
}
