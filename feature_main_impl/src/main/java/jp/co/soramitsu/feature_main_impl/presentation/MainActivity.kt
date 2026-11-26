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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.ui_core.lit.LitCutGlassPiece
import jp.co.soramitsu.ui_core.sacred.TenmonFlight
import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import androidx.core.view.get
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.AttrRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.Insetter
import dev.chrisbanes.insetter.windowInsetTypesOf
import java.util.Date
import kotlin.math.abs
import javax.inject.Inject
import jp.co.soramitsu.common.account.WalletStartupPass
import jp.co.soramitsu.common.domain.BarsColorHandler
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.presentation.compose.neon.safemode.WalletSessionState
import jp.co.soramitsu.common.presentation.view.ToolbarActivity
import jp.co.soramitsu.common.util.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.attrColor
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
        private const val ACTION_CHANGE_LANGUAGE =
            "jp.co.soramitsu.feature_main_impl.ACTION_CHANGE_LANGUAGE"

        private const val ACTION_ACCOUNT_ADDED =
            "jp.co.soramitsu.feature_main_impl.ACTION_ACCOUNT_ADDED"

        const val ACTION_DARK_THEME_TOGGLED =
            "jp.co.soramitsu.feature_main_impl.DARK_THEME"

        private const val IDLE_MINUTES: Long = 5

        /**
         * Opens the wallet in a task of its own. When the task of the screen that opens it is
         * pinned (screen pinning, lock task mode), that task can be neither left nor cleared, so
         * the wallet opens on top in it and that screen closes. Without this a pinned app that
         * the startup screen checked again stayed on the startup screen.
         */
        fun start(context: Context) {
            val pinnedTask = pinnedScreen(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = if (pinnedTask != null) 0 else Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            // While the launch scene plays, this screen carries it on from its first frame: nothing fades between.
            val carriesLaunchScene = TenmonFlight.isPlaying
            val options = ActivityOptions.makeCustomAnimation(
                context,
                if (carriesLaunchScene) 0 else android.R.anim.fade_in,
                if (carriesLaunchScene) 0 else android.R.anim.fade_out
            )
            context.startActivity(intent, options.toBundle())
            pinnedTask?.finish()
        }

        private fun pinnedScreen(context: Context): Activity? {
            val activity = context as? Activity ?: return null
            val state = activity.getSystemService(ActivityManager::class.java)?.lockTaskModeState
            return activity.takeIf { state != null && state != ActivityManager.LOCK_TASK_MODE_NONE }
        }

        fun restartAfterLanguageChange(context: Context) {
            TrustedMainRestart.grant(ACTION_CHANGE_LANGUAGE)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                action = ACTION_CHANGE_LANGUAGE
            }
            context.startActivity(intent)
        }

        fun restartAfterAddAccount(context: Context) {
            TrustedMainRestart.grant(ACTION_ACCOUNT_ADDED)
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
    lateinit var polkaswapRouter: PolkaswapRouter

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    @Inject
    lateinit var darkThemeManager: DarkThemeManager

    private var timeInBackground: Date? = null

    private var navController: NavController? = null

    override val viewModel: MainViewModel by viewModels()

    override fun layoutResource() = ActivityMainBinding.inflate(layoutInflater)

    private var restoredFromSavedState = false

    /**
     * The wallet opens only in a process that came through the startup screen
     * ([WalletStartupPass]). Android can start this screen directly in a new process: the task
     * comes back from Recents after an update, or is restored after the process was killed. Such
     * a start goes to the startup screen first, exactly like a start from the launcher, so the
     * upgrade checks, the wallet verification and its repairs cannot be skipped. The PIN is
     * asked there as on every start; a restart that would have skipped it is not honoured.
     */
    override fun mayStart(): Boolean {
        if (WalletStartupPass.isGranted()) return true
        TrustedMainRestart.consume(intent?.action)
        WalletStartupPass.openStartup(this)
        finish()
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        restoredFromSavedState = savedInstanceState != null
        super.onCreate(savedInstanceState)
    }

    override fun initViews() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigationView) { _, insets ->
            insets
        }
        // Clear of the bars and the keyboard, and of the camera cutout and a side navigation bar when the phone
        // lies on its side: nothing of the wallet is drawn under them.
        val sides = windowInsetTypesOf(navigationBars = true, displayCutout = true)
        Insetter.builder()
            .paddingTop(windowInsetTypesOf(statusBars = true, displayCutout = true))
            .paddingBottom(windowInsetTypesOf(navigationBars = true, ime = true, displayCutout = true))
            .paddingLeft(sides)
            .paddingRight(sides)
            .applyToView(binding.clMainContainer)

        binding.bottomNavigationView.menu[2].isEnabled = false
        val middleItem = binding.bottomNavigationView.findViewById<View>(R.id.polkaswap_nav_graph)
        middleItem.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        // The disc takes touches over the bar's middle item and nowhere else. On a phone that item is a fifth of
        // the bar; on a tablet the five items are narrower than that and stand together in the bar's middle, where
        // a fifth of the bar would also cover part of Explore and Activity. So the share is measured, whenever
        // the item or the bar is laid out again (a resized window can change the bar and leave the item as it was).
        val fitDiscToMiddleItem = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val bar = binding.bottomNavigationView.width
            if (bar > 0 && middleItem.width > 0) {
                val share = middleItem.width / bar.toFloat()
                val params = binding.fabMain.layoutParams as ConstraintLayout.LayoutParams
                if (abs(params.matchConstraintPercentWidth - share) > 0.002f) {
                    params.matchConstraintPercentWidth = share
                    binding.fabMain.layoutParams = params
                }
            }
        }
        middleItem.addOnLayoutChangeListener(fitDiscToMiddleItem)
        binding.bottomNavigationView.addOnLayoutChangeListener(fitDiscToMiddleItem)

        ViewCompat.setAccessibilityDelegate(binding.fabMain, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        })
        // The Polkaswap button: a disc of cut red glass standing in the bar, the flower inlaid in white. A finger
        // sets it down on the bar.
        val discPressed = mutableStateOf(false)
        binding.fabMain.onPressedChange = { discPressed.value = it }
        binding.fabDome.setContent {
            SoraAppTheme {
                Box(Modifier.fillMaxSize().padding(top = 2.dp), contentAlignment = Alignment.TopCenter) {
                    LitCutGlassPiece(
                        size = DpSize(35.dp, 35.dp),
                        radius = 17.5.dp,
                        glyph = painterResource(jp.co.soramitsu.common.R.drawable.ic_polkaswap_40),
                        glyphSize = DpSize(25.dp, 25.dp),
                        glyphKey = "polkaswap",
                        pressed = discPressed.value,
                    )
                }
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

            // Restored after the process was killed: a secret screen does not come back with it.
            if (restoredFromSavedState) mainRouter.closeSecretScreensLeftOpen()

            val trustedRestart = TrustedMainRestart.consume(intent.action)
            when {
                trustedRestart && intent.action == ACTION_CHANGE_LANGUAGE ->
                    chooseBottomNavigationItem(R.id.profile_nav_graph)
                trustedRestart || intent.action == ACTION_DARK_THEME_TOGGLED -> {}
                else -> viewModel.showPinFragment()
            }
        }

        // The launch scene the startup screen began carries on over the first screen, and uncovers it.
        if (!restoredFromSavedState) TenmonFlight.carryOn(this)
    }

    override fun onDestroy() {
        navController?.let {
            mainRouter.detachNavController(it)
        }
        super.onDestroy()
    }

    override fun subscribe(viewModel: MainViewModel) {
        viewModel.badConnectionVisibilityLiveData.observe(
            this
        ) {
            binding.badConnectionView.visibility = if (it) View.VISIBLE else View.GONE
        }

        // Always the ordinary unlock, also for a 4-digit PIN from SORA 2.x: the PIN screen takes
        // its length, and an update never stops the owner to make a new PIN.
        viewModel.isPincodeUpdateNeeded.observe(this) {
            mainRouter.showPin(PinCodeAction.TIMEOUT_CHECK)
        }
    }

    override fun setColor(@AttrRes color: Int) {
        val barColor = attrColor(color)
        window.setStatusBarColorCompat(barColor)
        window.setNavigationBarColorCompat(barColor)
    }

    override fun showBottomBar() {
        requireBinding()?.fabMain?.show()
        requireBinding()?.bottomBarShadow?.show()
        requireBinding()?.bottomNavigationView?.show()
    }

    override fun hideBottomBar() {
        requireBinding()?.fabMain?.gone()
        requireBinding()?.bottomBarShadow?.gone()
        requireBinding()?.bottomNavigationView?.gone()
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
        timeInBackground?.let { hiddenAt ->
            // A screen that shows a secret never comes back by itself. A lock screen here would
            // reopen it with a fingerprint, where opening it takes the typed PIN.
            WalletSessionState.markReturnedFromBackground()
            mainRouter.closeSecretScreensLeftOpen()
            if (idleTimePassedFrom(hiddenAt)) viewModel.showPinFragment()
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
        multiaccountStarter.startOnboardingFlow(this, false)
    }
}
