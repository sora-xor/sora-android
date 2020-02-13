/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.Navigation
import jp.co.soramitsu.common.presentation.view.ToolbarActivity
import jp.co.soramitsu.common.util.OnboardingState
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.personal_info.PersonalInfoFragment
import jp.co.soramitsu.feature_onboarding_impl.presentation.phone.PhoneNumberFragment
import jp.co.soramitsu.feature_onboarding_impl.presentation.privacy.PrivacyFragment
import jp.co.soramitsu.feature_onboarding_impl.presentation.verification.VerificationFragment
import jp.co.soramitsu.feature_onboarding_impl.presentation.version.UnsupportedVersionFragment
import javax.inject.Inject

class OnboardingActivity : ToolbarActivity<OnboardingViewModel>(), OnboardingRouter {

    companion object {

        private const val KEY_ONBOARDING_STATE = "onboarding_state"
        const val ACTION_INVITE = "jp.co.soramitsu.feature_onboarding_impl.ACTION_INVITE"

        fun start(context: Context, state: OnboardingState) {
            val intent = Intent(context, OnboardingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(KEY_ONBOARDING_STATE, state)
            }
            context.startActivity(intent)
        }

        fun startWithInviteLink(context: Context) {
            val intent = Intent(context, OnboardingActivity::class.java).apply {
                action = ACTION_INVITE
            }
            context.startActivity(intent)
        }
    }

    @Inject lateinit var mainStarter: MainStarter

    private lateinit var navController: NavController

    override fun layoutResource(): Int {
        return R.layout.activity_onboarding
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(this, OnboardingFeatureApi::class.java)
            .onboardingComponentBuilder()
            .withActivity(this)
            .build()
            .inject(this)
    }

    override fun initViews() {
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

    override fun showPersonalInfo(countryIso: String) {
        PersonalInfoFragment.newInstance(navController, countryIso)
    }

    override fun showMnemonic() {
        val bundle = Bundle()
        val navBuilder = NavOptions.Builder().setPopUpTo(navController.graph.id, true)
        navController.navigate(R.id.mnemonicFragment, bundle, navBuilder.build())
    }

    override fun showVerification(countryIso: String, blockingTime: Int) {
        VerificationFragment.newInstance(navController, countryIso, blockingTime)
    }

    override fun showMainScreen() {
        mainStarter.start(this)
    }

    override fun showRecovery() {
        navController.navigate(R.id.recoveryFragment)
    }

    override fun onBackButtonPressed() {
        navController.popBackStack()
    }

    override fun showTermsScreen() {
        navController.navigate(R.id.termsFragment)
    }

    override fun showBrowser(link: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(link)
        }
        startActivity(intent)
    }

    override fun showCountries() {
        navController.navigate(R.id.selectCountryFragment)
    }

    override fun showPhoneNumber(countryIso: String, phoneCode: String) {
        PhoneNumberFragment.newInstance(navController, countryIso, phoneCode)
    }

    override fun showUnsupportedScreen(appUrl: String) {
        UnsupportedVersionFragment.newInstance(appUrl, navController)
    }

    override fun showPrivacyScreen() {
        PrivacyFragment.start(navController)
    }
}