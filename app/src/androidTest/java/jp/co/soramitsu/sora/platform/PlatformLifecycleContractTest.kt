package jp.co.soramitsu.sora.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.Navigation
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.presentation.view.ToastDialog
import jp.co.soramitsu.common.util.TimerWrapper
import jp.co.soramitsu.common.util.ext.*
import jp.co.soramitsu.feature_main_impl.presentation.inappupdate.FlexibleUpdateDialog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

class LifecycleProbeFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, state: Bundle?): View = FrameLayout(requireContext())
}

@RunWith(AndroidJUnit4::class)
class PlatformLifecycleContractTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingActivity>
    @Before fun start() {
        scenario = ActivityScenario.launch(jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingActivity::class.java)
        scenario.onActivity { it.setContentView(FrameLayout(it).apply { id = android.R.id.content }) }
    }
    @After fun stop() { if (::scenario.isInitialized) scenario.close() }

    @Test fun timerEmitsRealTicksAndCancellationStopsFurtherUpdates() = runBlocking {
        val timer = TimerWrapper()
        var ticks: Flow<Long>? = null
        scenario.onActivity { timer.cancel(); assertFalse(timer.isStarted()); ticks = timer.start(2_000, 50) }
        val tick = withTimeout(5_000) { ticks!!.first { it in 1..1_999 } }
        scenario.onActivity { assertTrue(timer.isStarted()); timer.cancel(); timer.cancel(); assertFalse(timer.isStarted()) }
        val last = ticks!!.first()
        delay(150)
        assertEquals(last, ticks!!.first()); assertTrue(tick > 0)
        assertEquals("01:02:03", timer.calcTimeLeft(3_723_000))
        assertEquals("01h 02m", timer.formatTime(3_723_000))
        assertEquals("02h 03m", timer.formatTime(123_000))
    }
    @Test fun toastShowsItsLocalizedMessageAndIconThenDismissesAutomatically() {
        lateinit var dialog: ToastDialog
        scenario.onActivity {
            dialog = ToastDialog(R.drawable.ic_checkmark_24, R.string.common_copied, 1_000, it)
            dialog.show()
            val message = dialog.findViewById<TextView>(R.id.text)
            assertEquals(it.getString(R.string.common_copied), message.text.toString())
            assertNotNull(message.compoundDrawables[1]); assertTrue(dialog.isShowing)
        }
        compose.waitUntil(5_000) { !dialog.isShowing }
    }
    @Test fun fragmentDimensionsArgumentsAndDelayedCallbacksUseTheirAttachedLifecycle() = runBlocking {
        val calls = AtomicInteger()
        val fragment = LifecycleProbeFragment()
        fragment.arguments = Bundle().apply { putParcelable("route", Intent("fixture.action")) }
        scenario.onActivity { activity ->
            activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
            assertEquals((7 * activity.resources.displayMetrics.density).toInt(), fragment.dp2px(7))
            assertEquals("fixture.action", fragment.requireParcelable<Intent>("route").action)
            assertTrue(runCatching { fragment.requireParcelable<Intent>("missing") }.exceptionOrNull() is IllegalArgumentException)
            fragment.runDelayed(30) { calls.incrementAndGet() }
        }
        withTimeout(5_000) { while (calls.get() == 0) delay(10) }
        scenario.onActivity { activity ->
            fragment.runDelayed(100) { calls.incrementAndGet() }
            fragment.setStatusBarColor(Color.Red); fragment.setNavbarColor(Color.Blue)
            fragment.hideSoftKeyboard()
            activity.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
        }
        delay(150); assertEquals(1, calls.get())
    }
    @Test fun safeLaunchPassesTheExactIntentAndContainsMissingActivityErrors() {
        val attempts = mutableListOf<Intent>()
        scenario.onActivity { activity ->
            val context = object : ContextWrapper(activity) {
                override fun startActivity(intent: Intent) { attempts += intent; if (intent.action == "missing") throw ActivityNotFoundException() }
            }
            val valid = Intent("fixture"); val missing = Intent("missing")
            context.safeStartActivity(valid, ResponseCode.NOW_BROWSER_FOUND)
            context.safeStartActivity(missing, ResponseCode.NOW_BROWSER_FOUND)
            assertEquals(listOf(valid, missing), attempts)
        }
    }

    @Test fun contextThemeAndActivityResolutionPreserveTheAttachedHost() {
        scenario.onActivity { activity ->
            assertSame(activity, activity.getActivity())
            assertSame(activity, ContextWrapper(activity).getActivity())
            assertNull(activity.applicationContext.getActivity())
            assertEquals(android.graphics.Color.BLACK, (null as Context?).attrColor(android.R.attr.colorAccent))
            val value = android.util.TypedValue()
            assertTrue(activity.theme.resolveAttribute(android.R.attr.colorAccent, value, true))
            assertEquals(value.data, activity.colorFromAttr(android.R.attr.colorAccent))
            assertEquals(value.data, activity.getColorFromAttrs(android.R.attr.colorAccent).data)
            assertEquals(activity.resources.getDimensionPixelSize(android.R.dimen.app_icon_size), activity.dpRes2px(android.R.dimen.app_icon_size))
        }
    }

    @Test fun keyboardHelpersRetainFocusAndTolerateContextsWithoutAnActivity() {
        scenario.onActivity { activity ->
            val input = android.widget.EditText(activity)
            activity.setContentView(input)
            activity.openSoftKeyboard(input)
            assertTrue(input.hasFocus())
            activity.hideSoftKeyboard()
            ContextWrapper(activity).hideSoftKeyboard()
            activity.applicationContext.hideSoftKeyboard()
        }
    }
    private fun updateDecision(accepted: Boolean) {
        val state = SavedStateHandle()
        val previous = mockk<NavBackStackEntry> { every { savedStateHandle } returns state }
        val controller = mockk<NavController> {
            every { previousBackStackEntry } returns previous
            every { popBackStack() } returns true
        }
        val dark = MutableStateFlow(false)
        val fragment = FlexibleUpdateDialog().apply {
            darkThemeManager = mockk<DarkThemeManager> { every { darkModeStatusFlow } returns dark }
        }
        var expected = ""
        scenario.onActivity { activity ->
            Navigation.setViewNavController(activity.findViewById(android.R.id.content), controller)
            activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
            expected = activity.getString(if (accepted) R.string.common_update else R.string.common_cancel)
        }
        compose.onNode(hasText(expected) and hasClickAction()).performClick()
        assertEquals(accepted, state.get<Boolean>(FlexibleUpdateDialog.UPDATE_REPLY))
        verify(exactly = 1) { controller.popBackStack() }
    }
    @Test fun updateDialogReturnsExplicitAcceptanceToThePreviousDestination() = updateDecision(true)
    @Test fun updateDialogReturnsCancellationToThePreviousDestination() = updateDecision(false)
}
