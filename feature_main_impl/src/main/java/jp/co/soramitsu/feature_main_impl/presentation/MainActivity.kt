/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.view.KeyEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.view.ToolbarActivity
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_ethereum_api.EthServiceStarter
import jp.co.soramitsu.feature_ethereum_api.EthStatusPollingServiceStarter
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.pincode.PincodeFragment
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_sse_api.EventsObservingStarter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import kotlinx.android.synthetic.main.activity_main.*
import java.util.Date
import javax.inject.Inject

class MainActivity : ToolbarActivity<MainViewModel>(), BottomBarController {

    companion object {
        const val ACTION_INVITE = "jp.co.soramitsu.feature_main_impl.ACTION_INVITE"

        private const val ACTION_CHANGE_LANGUAGE = "jp.co.soramitsu.feature_main_impl.ACTION_CHANGE_LANGUAGE"

        private const val IDLE_MINUTES: Long = 5
        private const val ANIM_START_POSITION = 100f
        private const val ANIM_DURATION = 150L
        private const val SERVICE_START_DELAY = 500L

        fun start(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }

        fun startWithInvite(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_INVITE
            }
            context.startActivity(intent)
        }

        fun restartAfterLanguageChange(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                action = ACTION_CHANGE_LANGUAGE
            }
            context.startActivity(intent)
        }
    }

    @Inject
    lateinit var mainRouter: MainRouter
    @Inject
    lateinit var eventsObservingStarter: EventsObservingStarter
    @Inject
    lateinit var ethServiceStarter: EthServiceStarter
    @Inject
    lateinit var ethStatusPollingServiceStarter: EthStatusPollingServiceStarter

    private var timeInBackground: Date? = null

    private var navController: NavController? = null

    override fun layoutResource(): Int {
        return R.layout.activity_main
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(this, MainFeatureApi::class.java)
            .mainComponentBuilder()
            .withActivity(this)
            .build()
            .inject(this)
    }

    override fun onPause() {
        super.onPause()
        eventsObservingStarter.stopObserver()
        ethStatusPollingServiceStarter.stopEthStatusPollingServiceService()
    }

    override fun initViews() {
        bottomNavigationView.show()
        bottomNavigationView.inflateMenu(R.menu.bottom_navigations)

        navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        mainRouter.attachNavController(navController!!)
        NavigationUI.setupWithNavController(bottomNavigationView, navController!!)

        if (ACTION_CHANGE_LANGUAGE == intent.action) {
            mainRouter.showProfile()
        } else {
            showPin(PinCodeAction.TIMEOUT_CHECK)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainRouter.detachNavController(navController!!)
    }

    override fun subscribe(viewModel: MainViewModel) {
        viewModel.showInviteErrorTimeIsUpLiveData.observe(this, EventObserver {
            AlertDialog.Builder(this)
                .setMessage(R.string.invite_enter_error_time_is_up)
                .setPositiveButton(R.string.common_ok) { _, _ -> }
                .show()
        })

        viewModel.showInviteErrorAlreadyAppliedLiveData.observe(this, EventObserver {
            AlertDialog.Builder(this)
                .setMessage(R.string.invite_enter_error_already_applied)
                .setPositiveButton(R.string.common_ok) { _, _ -> }
                .show()
        })

        viewModel.badConnectionVisibilityLiveData.observe(this, Observer {
            if (it) {
                showBadConnectionView()
            } else {
                hideBadConnectionView()
            }
        })

        viewModel.ethereumConfigStateLiveData.observe(this, Observer {
            if (it) {
                hideBadConnectionView()
            } else {
                showBadConnectionView(R.string.ethereum_config_unavailable)
            }
        })

        viewModel.addInviteIsPossibleLiveData.observe(this, EventObserver {
            val message = getString(R.string.invite_enter_confirmation_body_mask, it)
            AlertDialog.Builder(this)
                .setMessage(message)
                .setNegativeButton(R.string.common_cancel) { _, _ -> }
                .setPositiveButton(R.string.common_apply) { _, _ -> viewModel.applyInvitationCode() }
                .show()
        })

        viewModel.invitationCodeAppliedSuccessful.observe(this, EventObserver {
            AlertDialog.Builder(this)
                .setTitle(R.string.invite_code_applied_title)
                .setMessage(R.string.invite_code_applied_body)
                .setPositiveButton(R.string.common_ok) { _, _ -> }
                .show()
        })
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            if (ACTION_INVITE == it.action) {
                viewModel.startedWithInviteAction()
            }
        }
    }

    private fun showBadConnectionView(@StringRes content: Int = R.string.common_network_unavailable) {
        if (View.GONE == badConnectionView.visibility) {
            badConnectionView.setText(content)
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

    private fun showPin(action: PinCodeAction) {
        mainRouter.showPin(action)
    }

    fun checkInviteAction() {
        if (ACTION_INVITE == intent.action) {
            viewModel.startedWithInviteAction()
        }
    }

    fun startEthService() {
        ethServiceStarter.startEthService()
    }

    override fun showBottomBar() {
        bottomNavigationView.show()
    }

    override fun hideBottomBar() {
        bottomNavigationView.gone()
    }

    fun restartAfterLanguageChange() {
        restartAfterLanguageChange(this)
    }

    fun closeApp() {
        finish()
    }

    fun restartApp() {
        FeatureUtils.getFeature<OnboardingFeatureApi>(application, OnboardingFeatureApi::class.java)
            .provideOnboardingStarter()
            .start(this, OnboardingState.INITIAL)
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
        runServices()
    }

    private fun runServices() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningAppProcesses = activityManager.runningAppProcesses
        if (runningAppProcesses != null) {
            val importance = runningAppProcesses[0].importance

            if (importance <= RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                Handler().postDelayed({
                    eventsObservingStarter.startObserver()
                    ethStatusPollingServiceStarter.startEthStatusPollingServiceService()
                }, SERVICE_START_DELAY)
            }
        }
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
            if (mainRouter.currentDestinationIsPincode()) {
                val navHostFragment = supportFragmentManager.fragments[0] as NavHostFragment?

                if (navHostFragment != null) {
                    (navHostFragment.childFragmentManager.fragments[navHostFragment.childFragmentManager.fragments.size - 1] as PincodeFragment)
                        .onBackPressed()
                }
                return true
            }

            if (mainRouter.currentDestinationIsUserVerification()) {
                closeApp()
                return true
            }
            return super.onKeyUp(keyCode, event)
        }
        return super.onKeyUp(keyCode, event)
    }
}