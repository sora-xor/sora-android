/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import jp.co.soramitsu.common.presentation.view.ToolbarActivity
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.OnboardingState
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.about.AboutFragment
import jp.co.soramitsu.feature_main_impl.presentation.contacts.ContactsFragment
import jp.co.soramitsu.feature_main_impl.presentation.pincode.PinCodeAction
import jp.co.soramitsu.feature_main_impl.presentation.pincode.PincodeFragment
import jp.co.soramitsu.feature_main_impl.presentation.privacy.PrivacyFragment
import jp.co.soramitsu.feature_main_impl.presentation.transactionconfirmation.TransactionConfirmationFragment
import jp.co.soramitsu.feature_main_impl.presentation.transactiondetails.TransactionDetailsFragment
import jp.co.soramitsu.feature_main_impl.presentation.transfer.TransferAmountFragment
import jp.co.soramitsu.feature_main_impl.presentation.version.UnsupportedVersionFragment
import jp.co.soramitsu.feature_main_impl.presentation.withdrawal.WithdrawalAmountFragment
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import kotlinx.android.synthetic.main.activity_main.badConnectionView
import java.net.URL
import java.util.Date
import javax.inject.Inject

class MainActivity : ToolbarActivity(), MainRouter {

    companion object {
        private const val IDLE_MINUTES: Long = 5
        private const val ACTION_INVITE = "jp.co.soramitsu.feature_main_impl.ACTION_INVITE"
        private const val ANIM_START_POSITION = 100f
        private const val ANIM_DURATION = 150L

        fun start(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }

        fun startWithInvite(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_INVITE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    @Inject lateinit var mainViewModel: MainViewModel

    private var timeInBackground: Date? = null

    private lateinit var bottomNavigationView: BottomNavigationView

    private lateinit var navController: NavController

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        FeatureUtils.getFeature<MainFeatureComponent>(this, MainFeatureApi::class.java)
            .mainComponentBuilder()
            .withActivity(this)
            .build()
            .inject(this)
        initNavigation()

        showPin(PinCodeAction.TIMEOUT_CHECK)

        mainViewModel.showInviteErrorLiveData.observe(this, EventObserver {
            if (ACTION_INVITE == intent.action) {
                AlertDialog.Builder(this)
                    .setMessage(R.string.you_have_already_registered)
                    .setPositiveButton(R.string.sora_ok) { _, _ ->
                    }
                    .show()
            }
        })

        mainViewModel.badConnectionVisibilityLiveData.observe(this, Observer {
            if (it) {
                showBadConnectionView()
            } else {
                hideBadConnectionView()
            }
        })
    }

    private fun showBadConnectionView() {
        if (View.GONE == badConnectionView.visibility) {
            val animation = TranslateAnimation(0f, 0f, -ANIM_START_POSITION, 0f)
            animation.duration = ANIM_DURATION
            badConnectionView.startAnimation(animation)
            badConnectionView.show()
        }
    }

    private fun hideBadConnectionView() {
        if (View.VISIBLE == badConnectionView.visibility) {
            val animation = TranslateAnimation(0f, 0f, 0f, -ANIM_START_POSITION)
            animation.duration = ANIM_DURATION
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationRepeat(p0: Animation?) {
                }

                override fun onAnimationEnd(p0: Animation?) {
                    badConnectionView.gone()
                }

                override fun onAnimationStart(p0: Animation?) {
                }
            })
            badConnectionView.startAnimation(animation)
        }
    }

    private fun initNavigation() {
        bottomNavigationView = findViewById(R.id.navigation)
        bottomNavigationView.show()
        bottomNavigationView.inflateMenu(R.menu.bottom_navigations)
        navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        navController.setGraph(R.navigation.main_nav_graph)
        NavigationUI.setupWithNavController(bottomNavigationView, navController)
    }

    override fun showPin(action: PinCodeAction) {
        val bundle = Bundle().apply {
            putSerializable(Const.PIN_CODE_ACTION, action)
        }

        navController.navigate(R.id.pincodeFragment, bundle)
    }

    override fun showMain() {
        navController.navigate(R.id.mainFragment)
    }

    override fun showInvite() {
        navController.navigate(R.id.inviteFragment)
    }

    override fun showPersonalDataEdition() {
        navController.navigate(R.id.personalDataEditFragment)
    }

    override fun hidePinCode() {
        navController.popBackStack()
        if (ACTION_INVITE == intent.action) {
            mainViewModel.inviteAction()
        }
    }

    override fun popBackStackFragment() {
        navController.popBackStack()
    }

    override fun showBrowser(link: URL) {
        showBrowser(link.toString())
    }

    override fun showBrowser(link: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(link)
        }
        startActivity(intent)
    }

    override fun showTermsFragment() {
        navController.navigate(R.id.termsFragment)
    }

    override fun showProjectDetailed(projectId: String) {
        val bundle = Bundle().apply {
            putString(Const.PROJECT_ID, projectId)
        }

        navController.navigate(R.id.projectDetailFragment, bundle)
    }

    override fun showBottomView() {
        bottomNavigationView.show()
    }

    override fun hideBottomView() {
        bottomNavigationView.gone()
    }

    override fun showReputationScreen() {
        navController.navigate(R.id.reputationFragment)
    }

    override fun showPassphrase() {
        navController.navigate(R.id.passphraseFragment)
    }

    override fun showPinCheckToPassphrase() {
        showPin(PinCodeAction.OPEN_PASSPHRASE)
    }

    override fun showFaq() {
        hideBottomView()
        navController.navigate(R.id.faqFragment)
    }

    override fun showConversion() {
        navController.navigate(R.id.conversionFragment)
    }

    override fun showVotesScreen() {
        navController.navigate(R.id.votesFragment)
    }

    override fun showContacts(balance: String) {
        ContactsFragment.start(balance, navController)
    }

    override fun showReceiveAmount() {
        navController.navigate(R.id.receive_amount_fragment)
    }

    override fun showTransferAmount(accountId: String, fullName: String, amount: String, description: String, balance: String) {
        TransferAmountFragment.start(accountId, fullName, amount, description, balance, navController)
    }

    override fun showTransactionConfirmation(accountId: String, fullName: String, amount: Double, description: String, fee: Double) {
        TransactionConfirmationFragment.start(accountId, fullName, amount, description, fee, navController)
    }

    override fun showTransactionConfirmationViaEth(amount: Double, ethAddress: String, notaryAddress: String, feeAddress: String, fee: Double) {
        TransactionConfirmationFragment.startEth(amount, ethAddress, notaryAddress, feeAddress, fee, navController)
    }

    override fun showTransactionDetails(
        recipient: String,
        transactionId: String,
        amount: Double,
        status: String,
        dateTime: Date,
        type: Transaction.Type,
        description: String,
        fee: Double
    ) {
        TransactionDetailsFragment.start("", recipient, transactionId, amount, "", status, dateTime, type, description, fee, false, navController)
    }

    override fun showTransactionDetails(
        amount: Double,
        status: String,
        dateTime: Date,
        type: Transaction.Type,
        description: String,
        fee: Double
    ) {
        TransactionDetailsFragment.start("", "", "", amount, "", status, dateTime, type, description, fee, false, navController)
    }

    override fun showTransactionDetailsFromList(recipientId: String, balance: String, recipient: String, transactionId: String, amount: Double, status: String, dateTime: Date, type: Transaction.Type, description: String, fee: Double) {
        TransactionDetailsFragment.start(recipientId, recipient, transactionId, amount, balance, status, dateTime, type, description, fee, true, navController)
    }

    override fun closeApp() {
        finish()
    }

    override fun restartApp() {
        FeatureUtils.getFeature<OnboardingFeatureApi>(application, OnboardingFeatureApi::class.java)
            .provideOnboardingStarter()
            .start(this, OnboardingState.INITIAL)
    }

    override fun returnToWalletFragment() {
        navController.popBackStack(R.id.walletFragment, false)
    }

    override fun showUnsupportedScreen(appUrl: String) {
        UnsupportedVersionFragment.newInstance(appUrl, navController)
    }

    override fun onTrimMemory(i: Int) {
        if (i == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            timeInBackground = Date()
        }
    }

    public override fun onResume() {
        if (timeInBackground != null && idleTimePassedFrom(timeInBackground!!)) {
            showPin(PinCodeAction.TIMEOUT_CHECK)
        }
        timeInBackground = null
        super.onResume()
    }

    private fun idleTimePassedFrom(timeInBackground: Date): Boolean {
        return Date().time - timeInBackground.time >= IDLE_MINUTES * 60 * 1000
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.isTracking && !event.isCanceled) {
            if (navController.currentDestination != null) {

                if (navController.currentDestination!!.id == R.id.pincodeFragment) {
                    val navHostFragment = supportFragmentManager.fragments[0] as NavHostFragment?

                    if (navHostFragment != null) {
                        (navHostFragment.childFragmentManager.fragments[navHostFragment.childFragmentManager.fragments.size - 1] as PincodeFragment)
                            .onBackPressed()
                    }
                    return true
                }
            }
            return super.onKeyUp(keyCode, event)
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun showWithdrawalAmountViaEth(balance: String) {
        WithdrawalAmountFragment.start(balance, navController)
    }

    override fun showPrivacy() {
        PrivacyFragment.start(navController)
    }

    override fun showAbout() {
        hideBottomView()
        AboutFragment.start(navController)
    }
}