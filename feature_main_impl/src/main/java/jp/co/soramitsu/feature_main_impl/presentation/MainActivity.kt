/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation

import android.app.ActivityOptions
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.Insetter
import dev.chrisbanes.insetter.applyInsetter
import dev.chrisbanes.insetter.windowInsetTypesOf
import jp.co.soramitsu.common.inappupdate.InAppUpdateManager
import jp.co.soramitsu.common.presentation.view.ToolbarActivity
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.ext.getColorAttr
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.ActivityMainBinding
import jp.co.soramitsu.feature_multiaccount_api.OnboardingNavigator
import jp.co.soramitsu.feature_onboarding_api.OnboardingStarter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity :
    ToolbarActivity<MainViewModel, ActivityMainBinding>(),
    BottomBarController,
    OnboardingNavigator,
    InAppUpdateManager.UpdateManagerListener {

    companion object {
        private const val ACTION_INVITE = "jp.co.soramitsu.feature_main_impl.ACTION_INVITE"

        private const val ACTION_CHANGE_LANGUAGE =
            "jp.co.soramitsu.feature_main_impl.ACTION_CHANGE_LANGUAGE"

        private const val ACTION_ACCOUNT_ADDED =
            "jp.co.soramitsu.feature_main_impl.ACTION_ACCOUNT_ADDED"

        private const val IDLE_MINUTES: Long = 5
        private const val ANIM_START_POSITION = 100f
        private const val ANIM_DURATION = 150L
        private const val REQUEST_CODE_UPDATE = 1233

        fun start(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val options = ActivityOptions.makeCustomAnimation(
                context,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            context.startActivity(intent, options.toBundle())
        }

        fun startWithInvite(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_INVITE
            }
            val options = ActivityOptions.makeCustomAnimation(
                context,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            context.startActivity(intent, options.toBundle())
        }

        fun restartAfterLanguageChange(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                action = ACTION_CHANGE_LANGUAGE
            }
            context.startActivity(intent)
        }

        fun restartAfterAddAccount(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                action = ACTION_ACCOUNT_ADDED
            }
            context.startActivity(intent)
        }
    }

    @Inject
    lateinit var mainRouter: MainRouter

    @Inject
    lateinit var inAppUpdateManager: InAppUpdateManager

    private var timeInBackground: Date? = null

    private var navController: NavController? = null

    private val vm: MainViewModel by viewModels()

    override val viewModel: MainViewModel
        get() = vm

    override fun layoutResource() = ActivityMainBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            inAppUpdateManager.start(this@MainActivity)
        }
    }

    override fun askUserToInstall() {
        findNavController(R.id.fragmentNavHostMain)
            .currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Boolean?>(FlexibleUpdateDialog.UPDATE_REPLY)?.observe(this) {
                if (it == true) inAppUpdateManager.startUpdateFlexible()
            }
        mainRouter.showFlexibleUpdateScreen()
    }

    override fun readyToShowFlexible(): Int? {
        return REQUEST_CODE_UPDATE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_UPDATE) {
            inAppUpdateManager.flexibleDesire(resultCode)
        }
    }

    override fun initViews() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigationView) { _, insets ->
            insets
        }
        Insetter.builder()
            .paddingTop(windowInsetTypesOf(statusBars = true))
            .applyToView(binding.flContainer)
        Insetter.builder()
            .paddingBottom(windowInsetTypesOf(navigationBars = true) or windowInsetTypesOf(ime = true))
            .applyToView(binding.clMainContainer)

        binding.bottomNavigationView.inflateMenu(R.menu.bottom_navigations)

        binding.fragmentNavHostMain.post {
            navController = supportFragmentManager.findFragmentById(R.id.fragmentNavHostMain)
                ?.findNavController()

            navController?.let {
                mainRouter.attachNavController(it)
                NavigationUI.setupWithNavController(binding.bottomNavigationView, it)
            }

            when (intent.action) {
                ACTION_CHANGE_LANGUAGE -> chooseBottomNavigationItem(R.id.profile_nav_graph)
                ACTION_ACCOUNT_ADDED -> {}
                else -> viewModel.showPinFragment()
            }
        }

        binding.badConnectionView.applyInsetter {
            type(statusBars = true) {
                padding(top = true)
            }
        }
    }

    override fun onDestroy() {
        navController?.let {
            mainRouter.detachNavController(it)
        }
        super.onDestroy()
    }

    override fun subscribe(viewModel: MainViewModel) {
        viewModel.showInviteErrorTimeIsUpLiveData.observe(
            this,
            EventObserver {
                AlertDialog.Builder(this)
                    .setMessage(R.string.invite_enter_error_time_is_up)
                    .setPositiveButton(R.string.common_ok) { _, _ -> }
                    .show()
            }
        )

        viewModel.showInviteErrorAlreadyAppliedLiveData.observe(
            this,
            EventObserver {
                AlertDialog.Builder(this)
                    .setMessage(R.string.invite_enter_error_already_applied)
                    .setPositiveButton(R.string.common_ok) { _, _ -> }
                    .show()
            }
        )

        viewModel.badConnectionVisibilityLiveData.observe(
            this
        ) {
            if (it) {
                showBadConnectionView()
            } else {
                hideBadConnectionView()
            }
        }

        viewModel.invitationCodeAppliedSuccessful.observe(
            this,
            EventObserver {
                AlertDialog.Builder(this)
                    .setTitle(R.string.invite_code_applied_title)
                    .setMessage(R.string.invite_code_applied_body)
                    .setPositiveButton(R.string.common_ok) { _, _ -> }
                    .show()
            }
        )

        viewModel.isPincodeUpdateNeeded.observe(this) { isUpdateNeeded ->
            if (isUpdateNeeded) {
                mainRouter.showPin(PinCodeAction.CHANGE_PIN_CODE)
            } else {
                mainRouter.showPin(PinCodeAction.TIMEOUT_CHECK)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
//            if (ACTION_INVITE == it.action) {
//                viewModel.startedWithInviteAction()
//            }
        }
    }

    private fun showBadConnectionView(@StringRes content: Int = R.string.common_connecting) {
        if (View.GONE == binding.badConnectionView.visibility) {
            val errorColor = binding.badConnectionView.getColorAttr(R.attr.statusError)
            binding.badConnectionView.setText(content)
            binding.badConnectionView.setBackgroundColor(errorColor)
            val animation = TranslateAnimation(0f, 0f, -ANIM_START_POSITION, 0f)
            animation.duration = ANIM_DURATION
            binding.badConnectionView.startAnimation(animation)
            binding.badConnectionView.show()
            window.statusBarColor = errorColor
        }
    }

    private fun hideBadConnectionView(@StringRes content: Int = R.string.common_connected) {
        if (View.VISIBLE == binding.badConnectionView.visibility) {
            val successColor = binding.badConnectionView.getColorAttr(R.attr.statusSuccess)
            binding.badConnectionView.setText(content)
            binding.badConnectionView.setBackgroundColor(successColor)
            window.statusBarColor = successColor
            val animation = TranslateAnimation(0f, 0f, 0f, -ANIM_START_POSITION)
            animation.duration = ANIM_DURATION
            animation.startOffset = 500
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationRepeat(p0: Animation?) {
                }

                override fun onAnimationEnd(p0: Animation?) {
                    binding.badConnectionView.gone()
                    window.statusBarColor =
                        binding.badConnectionView.getColorAttr(R.attr.baseBackground)
                }

                override fun onAnimationStart(p0: Animation?) {
                }
            })
            binding.badConnectionView.startAnimation(animation)
        }
    }

    fun checkInviteAction() {
//        if (ACTION_INVITE == intent.action) {
//            viewModel.startedWithInviteAction()
//        }
    }

    override fun showBottomBar() {
        requireBinding()?.bottomNavigationView?.show()
    }

    override fun hideBottomBar() {
        requireBinding()?.bottomNavigationView?.gone()
    }

    override fun navigateTabToSwap() {
        chooseBottomNavigationItem(R.id.polkaswap_nav_graph)
    }

    fun restartAfterLanguageChange() {
        restartAfterLanguageChange(this)
    }

    fun closeApp() {
        finish()
    }

    @Inject
    lateinit var onbs: OnboardingStarter

    fun restartApp() {
        onbs.start(this)
    }

    override fun onTrimMemory(i: Int) {
        if (i == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            timeInBackground = Date()
        }
    }

    public override fun onResume() {
        if (timeInBackground != null && (idleTimePassedFrom(timeInBackground!!) || mainRouter.currentDestinationIsPinCheckNeeded())) {
            viewModel.showPinFragment()
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

    private fun chooseBottomNavigationItem(itemId: Int) {
        (binding.bottomNavigationView.findViewById(itemId) as View).performClick()
    }

    override fun showOnboardingFlow() {
        onbs.start(this, false)
    }
}
