package jp.co.soramitsu.feature_main_impl.presentation

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import com.google.android.material.color.MaterialColors
import dev.chrisbanes.insetter.Insetter
import dev.chrisbanes.insetter.applyInsetter
import dev.chrisbanes.insetter.windowInsetTypesOf
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.view.ToolbarActivity
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.ActivityMainBinding
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.pincode.PincodeFragment
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import java.util.Date
import javax.inject.Inject

class MainActivity : ToolbarActivity<MainViewModel, ActivityMainBinding>(), BottomBarController {

    companion object {
        private const val ACTION_INVITE = "jp.co.soramitsu.feature_main_impl.ACTION_INVITE"

        private const val ACTION_CHANGE_LANGUAGE =
            "jp.co.soramitsu.feature_main_impl.ACTION_CHANGE_LANGUAGE"

        private const val IDLE_MINUTES: Long = 5
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

    private var timeInBackground: Date? = null

    private var navController: NavController? = null

    override fun layoutResource() = ActivityMainBinding.inflate(layoutInflater)

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(this, MainFeatureApi::class.java)
            .mainComponentBuilder()
            .withActivity(this)
            .build()
            .inject(this)
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
            mainRouter.attachNavController(navController!!)

            NavigationUI.setupWithNavController(binding.bottomNavigationView, navController!!)

            if (ACTION_CHANGE_LANGUAGE == intent.action) {
                chooseBottomNavigationItem(R.id.profile_nav_graph)
            } else {
                showPin(PinCodeAction.TIMEOUT_CHECK)
            }
        }

        binding.badConnectionView.applyInsetter {
            type(statusBars = true) {
                padding(top = true)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainRouter.detachNavController(navController!!)
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
            this,
            {
                if (it) {
                    showBadConnectionView()
                } else {
                    hideBadConnectionView()
                }
            }
        )

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
            val errorColor = MaterialColors.getColor(binding.badConnectionView, R.attr.statusError)
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
            val successColor =
                MaterialColors.getColor(binding.badConnectionView, R.attr.statusSuccess)
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
                        MaterialColors.getColor(binding.badConnectionView, R.attr.baseOnAccent)
                }

                override fun onAnimationStart(p0: Animation?) {
                }
            })
            binding.badConnectionView.startAnimation(animation)
        }
    }

    private fun showPin(action: PinCodeAction) {
        mainRouter.showPin(action)
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

            if (mainRouter.currentDestinationIsClaimFragment()) {
                closeApp()
                return true
            }
            return super.onKeyUp(keyCode, event)
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun chooseBottomNavigationItem(itemId: Int) {
        (binding.bottomNavigationView.findViewById(itemId) as View).performClick()
    }
}
