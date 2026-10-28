package jp.co.soramitsu.sora.accessibility

import android.graphics.Rect
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.view.doOnLayout
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.DragHandleView
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DragHandleAccessibilityTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)
    private lateinit var handle: DragHandleView
    private val moves = mutableListOf<Int>()
    private var starts = 0

    private fun show(canMove: (Int) -> Boolean = { true }) {
        val laidOut = CountDownLatch(1)
        activity.scenario.onActivity { host ->
            host.setTheme(R.style.AppTheme)
            handle = DragHandleView(host).apply {
                contentDescription = host.getString(R.string.asset_reorder)
                setImageResource(R.drawable.ic_drag_and_drop)
                doOnLayout { laidOut.countDown() }
                bindReordering({ starts++ }, canMove, { moves += it; true })
            }
            host.setContentView(FrameLayout(host).apply {
                // A laid-out, centered anchor gives the real popup enough room on either side.
                // Opening it at (0,0) before the first frame can create a clipped popup window.
                val size = (48 * resources.displayMetrics.density).roundToInt()
                addView(handle, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            })
        }
        assertTrue("The popup anchor must finish its first layout", laidOut.await(5, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        activity.scenario.onActivity {
            val visible = Rect()
            assertTrue("The popup anchor must be on screen", handle.getGlobalVisibleRect(visible))
            assertEquals(handle.width, visible.width())
            assertEquals(handle.height, visible.height())
        }
    }

    private fun accessibilityClick() {
        activity.scenario.onActivity {
            assertTrue(handle.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
        }
    }

    private fun renderedMenuItem(label: Int): ViewInteraction = onView(withText(label)).perform(
        object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)
            override fun getDescription() = "wait up to 5 seconds for the popup item to be 90 percent visible"
            override fun perform(controller: UiController, view: View) {
                // PopupWindow's reveal transition is not an Espresso idling resource. Wait for
                // the same visibility threshold as click(), then retain the real click action.
                val ready = isDisplayingAtLeast(90)
                val started = SystemClock.uptimeMillis()
                while (!ready.matches(view) && SystemClock.uptimeMillis() - started < 5_000) {
                    controller.loopMainThreadForAtLeast(16)
                }
                val visible = Rect().also(view::getGlobalVisibleRect)
                assertTrue("Popup item stayed clipped after rendering: $visible (${view.width} x ${view.height})", ready.matches(view))
            }
        },
    )

    @Test fun talkBackCanMoveAnItemUpWithoutStartingAPointerDrag() {
        show(); accessibilityClick()
        renderedMenuItem(R.string.asset_move_up).check(matches(isDisplayed())).perform(click())
        assertEquals(listOf(-1), moves); assertEquals(0, starts)
    }

    @Test fun keyboardActivationCanMoveAnItemDown() {
        show()
        activity.scenario.onActivity { assertTrue(handle.performClick()) }
        renderedMenuItem(R.string.asset_move_down).perform(click())
        assertEquals(listOf(1), moves); assertEquals(0, starts)
    }

    @Test fun firstItemOffersOnlyTheValidDirection() {
        show { it == 1 }; accessibilityClick()
        renderedMenuItem(R.string.asset_move_down).perform(click())
        assertEquals(listOf(1), moves)
    }

    @Test fun menuRechecksAvailabilityBeforeMoving() {
        var allowed = true
        show { allowed }; accessibilityClick()
        activity.scenario.onActivity { allowed = false }
        renderedMenuItem(R.string.asset_move_up).perform(click())
        assertTrue(moves.isEmpty())
    }

    @Test fun unavailableReorderingDoesNotStartAnAction() {
        show { false }; accessibilityClick()
        assertTrue(moves.isEmpty()); assertEquals(0, starts)
    }

    private fun touch(action: Int, x: Float = 20f, y: Float = 20f) {
        activity.scenario.onActivity {
            val event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), action, x, y, 0)
            try { handle.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }

    @Test fun dragStartsOnceOnPointerDownAndNotOnEveryMotionEvent() {
        show { false }
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_MOVE); touch(MotionEvent.ACTION_UP)
        assertEquals(1, starts); assertTrue(moves.isEmpty())
    }

    @Test fun disabledHandleDoesNotStartADrag() {
        show { false }
        activity.scenario.onActivity { handle.isEnabled = false }
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_CANCEL)
        assertEquals(0, starts); assertTrue(moves.isEmpty())
    }

    private fun assertMenuAbsent() {
        onView(withText(R.string.asset_move_up)).check(doesNotExist())
        onView(withText(R.string.asset_move_down)).check(doesNotExist())
        assertTrue(moves.isEmpty())
    }

    @Test fun stationaryPointerTapStillOpensTheReorderMenu() {
        show()
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_UP)
        renderedMenuItem(R.string.asset_move_down).check(matches(isDisplayed())).perform(click())
        assertEquals(listOf(1), moves); assertEquals(1, starts)
    }

    @Test fun releasingAfterDraggingDoesNotOpenASecondReorderMenu() {
        show()
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_MOVE, x = 100f)
        touch(MotionEvent.ACTION_UP, x = 100f)
        assertMenuAbsent(); assertEquals(1, starts)
    }

    @Test fun draggingBackToTheStartStillDoesNotBecomeAClick() {
        show()
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_MOVE, y = 100f)
        touch(MotionEvent.ACTION_MOVE)
        touch(MotionEvent.ACTION_UP)
        assertMenuAbsent(); assertEquals(1, starts)
    }

    @Test fun canceledPointerSequenceCannotOpenTheMenuOnALateRelease() {
        show()
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_CANCEL); touch(MotionEvent.ACTION_UP)
        assertMenuAbsent(); assertEquals(1, starts)
    }

    @Test fun releaseWithoutAPointerDownCannotOpenTheMenu() {
        show()
        touch(MotionEvent.ACTION_UP)
        assertMenuAbsent(); assertEquals(0, starts)
    }
}
