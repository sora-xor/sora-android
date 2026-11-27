package jp.co.soramitsu.feature_select_node_impl.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ResultOnceTest {
    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    private val handle = SavedStateHandle()
    private val delivered = mutableListOf<String>()

    @Test
    fun `a result is delivered once and cleared from the handle`() {
        handle[KEY] = "checked"

        observe(ViewOwner())

        assertEquals(listOf("checked"), delivered)
        assertNull(handle.get<String>(KEY))
    }

    @Test
    fun `coming back without a new PIN does not deliver the previous result again`() {
        val first = ViewOwner()
        observe(first)
        handle[KEY] = "checked"
        first.destroy()

        observe(ViewOwner())

        assertEquals(listOf("checked"), delivered)
    }

    @Test
    fun `each new result is delivered on the next return`() {
        handle[KEY] = "first"
        val first = ViewOwner()
        observe(first)
        first.destroy()
        handle[KEY] = "second"

        observe(ViewOwner())

        assertEquals(listOf("first", "second"), delivered)
    }

    @Test
    fun `nothing is delivered before there is a result`() {
        observe(ViewOwner())

        assertEquals(emptyList<String>(), delivered)
    }

    private fun observe(owner: LifecycleOwner) {
        handle.observeResultOnce<String>(KEY, owner) { delivered += it }
    }

    private class ViewOwner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
        override val lifecycle: Lifecycle get() = registry

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    private companion object {
        const val KEY = "result"
    }
}
