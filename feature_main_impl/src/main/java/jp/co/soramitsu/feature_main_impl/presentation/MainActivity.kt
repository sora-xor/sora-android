/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_main_impl.presentation

import android.app.ActivityOptions
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import androidx.core.view.get
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import androidx.activity.viewModels
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.Insetter
import dev.chrisbanes.insetter.applyInsetter
import dev.chrisbanes.insetter.windowInsetTypesOf
import java.util.Date
import javax.inject.Inject
import jp.co.soramitsu.common.domain.BarsColorHandler
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.domain.DarkThemeManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import jp.co.soramitsu.common.presentation.compose.theme.neon.FxPolicy
import jp.co.soramitsu.common.presentation.compose.theme.neon.FxTier
import jp.co.soramitsu.common.presentation.compose.theme.neon.DeviceFxSignals
import jp.co.soramitsu.common.presentation.compose.theme.neon.NeonEffectsPreference
import jp.co.soramitsu.common.presentation.view.ToolbarActivity
import jp.co.soramitsu.common.util.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.attrColor
import jp.co.soramitsu.common.util.ext.getColorAttr
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.setNavigationBarColorCompat
import jp.co.soramitsu.common.util.ext.setStatusBarColorCompat
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.ActivityMainBinding
import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter
import jp.co.soramitsu.feature_multiaccount_api.OnboardingNavigator
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider

@AndroidEntryPoint
class MainActivity :
    ToolbarActivity<MainViewModel, ActivityMainBinding>(),
    BottomBarController,
    BarsColorHandler,
    OnboardingNavigator {

    companion object {
        private const val ACTION_INVITE = "jp.co.soramitsu.feature_main_impl.ACTION_INVITE"

        private const val ACTION_CHANGE_LANGUAGE =
            "jp.co.soramitsu.feature_main_impl.ACTION_CHANGE_LANGUAGE"

        private const val ACTION_ACCOUNT_ADDED =
            "jp.co.soramitsu.feature_main_impl.ACTION_ACCOUNT_ADDED"

        const val ACTION_DARK_THEME_TOGGLED =
            "jp.co.soramitsu.feature_main_impl.DARK_THEME"

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

//    @Inject
//    lateinit var inAppUpdateManager: InAppUpdateManager

    @Inject
    lateinit var polkaswapRouter: PolkaswapRouter

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    @Inject
    lateinit var darkThemeManager: DarkThemeManager

    private var timeInBackground: Date? = null

    private var navController: NavController? = null

    override val viewModel: MainViewModel by viewModels()

    @AttrRes
    private var curBarsColor: Int = R.attr.baseBackground

    override fun layoutResource() = ActivityMainBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        lifecycleScope.launch {
//            inAppUpdateManager.start(this@MainActivity)
//        }
    }

//    override fun askUserToInstall() {
//        findNavController(R.id.fragmentNavHostMain)
//            .currentBackStackEntry?.savedStateHandle
//            ?.getLiveData<Boolean?>(FlexibleUpdateDialog.UPDATE_REPLY)?.observe(this) {
//                if (it == true) inAppUpdateManager.startUpdateFlexible()
//            }
//        mainRouter.showFlexibleUpdateScreen()
//    }

//    override fun readyToShowFlexible(): Int? {
//        return REQUEST_CODE_UPDATE
//    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == REQUEST_CODE_UPDATE) {
//            inAppUpdateManager.flexibleDesire(resultCode)
//        }
//    }

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

        binding.bottomNavigationView.menu[2].isEnabled = false

        ViewCompat.setAccessibilityDelegate(binding.fabMain, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NeonEffectsPreference.reduceEffects.collect { updateSwapEffects() }
            }
        }
        binding.fabMain.setOnClickListener {
            debounceClickHandler.debounceClick {
                polkaswapRouter.showSwap(SubstrateOptionsProvider.feeAssetId)
            }
        }

        binding.fragmentNavHostMain.post {
            navController = supportFragmentManager.findFragmentById(R.id.fragmentNavHostMain)
                ?.findNavController()

            navController?.let {
                mainRouter.attachNavController(it)
                NavigationUI.setupWithNavController(binding.bottomNavigationView, it)
            }

            when (intent.action) {
                ACTION_CHANGE_LANGUAGE -> chooseBottomNavigationItem(R.id.profile_nav_graph)
                ACTION_DARK_THEME_TOGGLED, ACTION_ACCOUNT_ADDED -> {}
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
            this
        ) {
            AlertDialog.Builder(this)
                .setMessage(R.string.invite_enter_error_time_is_up)
                .setPositiveButton(R.string.common_ok) { _, _ -> }
                .show()
        }

        viewModel.showInviteErrorAlreadyAppliedLiveData.observe(
            this
        ) {
            AlertDialog.Builder(this)
                .setMessage(R.string.invite_enter_error_already_applied)
                .setPositiveButton(R.string.common_ok) { _, _ -> }
                .show()
        }

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
            this
        ) {
            AlertDialog.Builder(this)
                .setTitle(R.string.invite_code_applied_title)
                .setMessage(R.string.invite_code_applied_body)
                .setPositiveButton(R.string.common_ok) { _, _ -> }
                .show()
        }

        viewModel.isPincodeUpdateNeeded.observe(this) { isUpdateNeeded ->
            if (isUpdateNeeded) {
                mainRouter.showPin(PinCodeAction.CHANGE_PIN_CODE)
            } else {
                mainRouter.showPin(PinCodeAction.TIMEOUT_CHECK)
            }
        }
    }

    override fun setColor(@AttrRes color: Int) {
        curBarsColor = color
        val barColor = attrColor(color)
        window.setStatusBarColorCompat(barColor)
        window.setNavigationBarColorCompat(barColor)
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
            window.setStatusBarColorCompat(errorColor)
        }
    }

    private fun hideBadConnectionView(@StringRes content: Int = R.string.common_connected) {
        if (View.VISIBLE == binding.badConnectionView.visibility) {
            val successColor = binding.badConnectionView.getColorAttr(R.attr.statusSuccess)
            binding.badConnectionView.setText(content)
            binding.badConnectionView.setBackgroundColor(successColor)
            window.setStatusBarColorCompat(successColor)
            val animation = TranslateAnimation(0f, 0f, 0f, -ANIM_START_POSITION)
            animation.duration = ANIM_DURATION
            animation.startOffset = 500
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationRepeat(p0: Animation?) {
                }

                override fun onAnimationEnd(p0: Animation?) {
                    binding.badConnectionView.gone()
                    window.setStatusBarColorCompat(
                        binding.badConnectionView.getColorAttr(curBarsColor)
                    )
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
        requireBinding()?.fabMain?.show()
        requireBinding()?.bottomNavigationView?.show()
    }

    override fun hideBottomBar() {
        requireBinding()?.fabMain?.gone()
        requireBinding()?.bottomNavigationView?.gone()
    }

    override fun isBottomBarVisible(): Boolean {
        return requireBinding()?.bottomNavigationView?.isVisible ?: false
    }

    fun restartAfterLanguageChange() {
        restartAfterLanguageChange(this)
    }

    fun closeApp() {
        finish()
    }

    @Inject
    lateinit var multiaccountStarter: MultiaccountStarter

    fun restartApp() {
//        onbs.start(this)
    }

    override fun onTrimMemory(i: Int) {
        super.onTrimMemory(i)
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
        updateSwapEffects()
    }

    private fun updateSwapEffects() {
        val policy = FxPolicy.from(DeviceFxSignals.read(this), NeonEffectsPreference.reduceEffects.value)
        requireBinding()?.fabMain?.setBackgroundResource(
            if (policy.glows && policy.tier != FxTier.MINIMAL) R.drawable.bg_neon_swap_glow else 0)
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
        multiaccountStarter.startOnboardingFlow(this, false)
    }
}
