/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import androidx.activity.viewModels
import androidx.navigation.NavController
import androidx.navigation.Navigation
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.Insetter
import dev.chrisbanes.insetter.windowInsetTypesOf
import jp.co.soramitsu.common.presentation.view.ToolbarActivity
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.databinding.ActivityOnboardingBinding
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingActivity :
    ToolbarActivity<OnboardingViewModel, ActivityOnboardingBinding>() {

    companion object {

        private const val KEY_ONBOARDING_STATE = "onboarding_state"
        const val ACTION_INVITE = "jp.co.soramitsu.feature_onboarding_impl.ACTION_INVITE"

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

    @Inject
    lateinit var mas: MultiaccountStarter

    override val viewModel: OnboardingViewModel by viewModels()

    private lateinit var navController: NavController

    override fun layoutResource() = ActivityOnboardingBinding.inflate(layoutInflater)

    override fun initViews() {
        Insetter.builder()
            .paddingTop(windowInsetTypesOf(statusBars = true))
            .paddingBottom(windowInsetTypesOf(navigationBars = true))
            .applyToView(binding.flContainerOnboarding)
        initNavigation()
    }

    override fun subscribe(viewModel: OnboardingViewModel) = Unit

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
}
