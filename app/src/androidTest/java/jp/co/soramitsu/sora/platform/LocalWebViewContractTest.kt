package jp.co.soramitsu.sora.platform

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView as AndroidWebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.compose.webview.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class LocalWebViewContractTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun web(view: View): AndroidWebView? = when (view) {
        is AndroidWebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { web(view.getChildAt(it)) }
        else -> null
    }
    @Test fun localPageRespectsDisabledJavascriptAndReportsCompletion() {
        val finished = AtomicInteger()
        compose.setContent { SoraAppTheme { WebView(WebViewState("about:blank", javaScriptEnabled = false, loading = false)) { finished.incrementAndGet() } } }
        compose.waitUntil(15_000) { finished.get() > 0 }
        compose.runOnIdle {
            val browser = requireNotNull(web(compose.activity.window.decorView))
            assertFalse(browser.settings.javaScriptEnabled); assertTrue(browser.settings.domStorageEnabled)
            assertEquals("about:blank", browser.url)
        }
    }
    @Test fun webDestinationUsesItsViewModelAndEndsTheLoadingStateAfterPageCompletion() {
        lateinit var vm: WebViewViewModel
        compose.runOnUiThread { vm = WebViewViewModel("Local document", "about:blank") }
        val fragment = WebViewFragment()
        fragment.javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme {
            val nav = rememberNavController(); val scroll = rememberScrollState()
            NavHost(nav, startDestination = theOnlyRoute) { with(fragment) { content(scroll, nav) } }
        } }
        compose.waitUntil(15_000) { !vm.state.loading }
        compose.runOnIdle {
            val browser = requireNotNull(web(compose.activity.window.decorView))
            assertTrue(browser.settings.javaScriptEnabled); assertTrue(browser.settings.domStorageEnabled)
            assertEquals("about:blank", browser.url); assertEquals("about:blank", vm.state.url)
        }
    }
}
