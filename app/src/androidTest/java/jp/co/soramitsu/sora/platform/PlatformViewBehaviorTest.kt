package jp.co.soramitsu.sora.platform

import android.graphics.Color
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.util.*
import jp.co.soramitsu.common.util.ext.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformViewBehaviorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = ContextThemeWrapper(instrumentation.targetContext, jp.co.soramitsu.sora.R.style.AppTheme)
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    @Test fun visibilityHelpersDistinguishHiddenLayoutSpaceFromRemovedContent() = main {
        val view = View(context)
        view.hide(); assertEquals(View.INVISIBLE, view.visibility)
        view.show(); assertEquals(View.VISIBLE, view.visibility)
        view.gone(); assertEquals(View.GONE, view.visibility)
        view.showOrGone(true); assertEquals(View.VISIBLE, view.visibility)
        view.showOrGone(false); assertEquals(View.GONE, view.visibility)
        view.showOrHide(true); assertEquals(View.VISIBLE, view.visibility)
        view.showOrHide(false); assertEquals(View.INVISIBLE, view.visibility)
        view.hideOrGone(true); assertEquals(View.INVISIBLE, view.visibility)
        view.hideOrGone(false); assertEquals(View.GONE, view.visibility)
    }

    @Test fun nullableLabelsClearVisibilityAndRestoreNewContent() = main {
        val view = TextView(context)
        view.showOrGone("First"); assertEquals("First", view.text.toString()); assertEquals(View.VISIBLE, view.visibility)
        view.showOrGone(null as String?); assertEquals(View.GONE, view.visibility)
        view.showOrHide("Second"); assertEquals("Second", view.text.toString()); assertEquals(View.VISIBLE, view.visibility)
        view.showOrHide(null as String?); assertEquals(View.INVISIBLE, view.visibility)
    }

    @Test fun loaderVisibilityControlsAnimatableAndToleratesStaticImages() = main {
        val view = ImageView(context)
        val frames = AnimationDrawable().apply { addFrame(ColorDrawable(Color.RED), 100); addFrame(ColorDrawable(Color.BLUE), 100) }
        view.setImageDrawable(frames)
        view.animateLoader(true); assertTrue(frames.isRunning); assertEquals(View.VISIBLE, view.visibility)
        view.animateLoader(true); assertTrue(frames.isRunning)
        view.animateLoader(false); assertFalse(frames.isRunning); assertEquals(View.INVISIBLE, view.visibility)
        view.animateLoader(false); assertFalse(frames.isRunning)
        view.setImageDrawable(ColorDrawable(Color.BLACK)); view.animateLoader(true)
        assertEquals(View.VISIBLE, view.visibility)
        view.setImageTint2(Color.RED); assertEquals(Color.RED, view.imageTintList?.defaultColor)
        view.setImageTint(android.R.color.black); assertEquals(Color.BLACK, view.imageTintList?.defaultColor)
    }

    @Test fun optionalEndDrawableUsesRequestedDimensionsAndCanBeRemoved() = main {
        val view = TextView(context)
        view.setDrawableEnd(android.R.drawable.ic_menu_info_details, 20, 30, android.R.color.black)
        val drawable = requireNotNull(view.compoundDrawablesRelative[2])
        assertEquals((20 * context.resources.displayMetrics.density).toInt(), drawable.bounds.width())
        assertEquals((30 * context.resources.displayMetrics.density).toInt(), drawable.bounds.height())
        view.setDrawableEnd(android.R.drawable.ic_menu_info_details)
        assertTrue(view.compoundDrawablesRelative[2].bounds.width() > 0)
        view.setDrawableEnd()
        assertTrue(view.compoundDrawablesRelative.all { it == null })
    }

    @Test fun viewAnimationStartsAndClearsOnTheRequestedState() = main {
        val view = View(context)
        val animation = AlphaAnimation(0f, 1f)
        view.doAnimation(true, animation); assertSame(animation, view.animation)
        view.doAnimation(false, animation); assertNull(view.animation)
    }

    @Test fun doneActionInvokesOnlyTheDoneCallback() = main {
        val field = EditText(context)
        var calls = 0
        field.onDoneClicked { calls++ }
        field.onEditorAction(EditorInfo.IME_ACTION_NEXT)
        assertEquals(0, calls)
        field.onEditorAction(EditorInfo.IME_ACTION_DONE)
        assertEquals(1, calls)
    }

    @Test fun textFlowEmitsEditsAndDetachesAfterCollection() = runBlocking {
        withContext(Dispatchers.Main) {
            val field = EditText(context)
            val observed = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(2000) { field.asFlow().first() } }
            yield()
            field.setText("new value")
            assertEquals("new value", observed.await())
            field.setText("after collection")
            assertEquals("after collection", field.text.toString())
        }
    }

    @Test fun delayedActionsRequireALifecycleAndCancelWhenItsOwnerIsDestroyed() = runBlocking {
        withContext(Dispatchers.Main) {
            val view = View(context)
            assertNull(view.runDelayed(0) { fail("Unowned view must not run work") })
            val owner = object : LifecycleOwner {
                val registry = LifecycleRegistry(this)
                override val lifecycle: Lifecycle get() = registry
            }
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            view.setViewTreeLifecycleOwner(owner)
            var count = 0
            view.runDelayed(0) { count++ }!!.join()
            assertEquals(1, count)
            val pending = view.runDelayed(60_000) { count++ }!!
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            pending.join()
            assertTrue(pending.isCancelled)
            assertEquals(1, count)
        }
    }
}
