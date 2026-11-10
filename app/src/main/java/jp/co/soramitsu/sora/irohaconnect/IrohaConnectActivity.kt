package jp.co.soramitsu.sora.irohaconnect

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.ReleaseSurfacePolicy

@AndroidEntryPoint
class IrohaConnectActivity : AppCompatActivity() {
    @Inject lateinit var darkThemeManager: DarkThemeManager

    private val viewModel: IrohaConnectViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ReleaseSurfacePolicy.irohaConnectAllowed) {
            // A build whose ReleaseSurfacePolicy does not offer IrohaConnect (the 3.9.x store
            // policy's production flavor) closes before any pairing state or signing UI is
            // created. The SORA Nexus line offers it in every flavor.
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            BackHandler {
                viewModel.closeByUser()
                finish()
            }
            val darkTheme by darkThemeManager.darkModeStatusFlow.collectAsStateWithLifecycle()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            val approvalToken = state.pairing?.sessionFingerprint ?: state.signing?.requestToken
            var confirmPin by remember(approvalToken, state.phase) { mutableStateOf(false) }
            SoraAppTheme(darkTheme = darkTheme) {
            IrohaConnectScreen(
                state = state,
                onApprovePairing = { confirmPin = true },
                onRejectPairing = viewModel::rejectPairing,
                onApproveSigning = { confirmPin = true },
                onRejectSigning = viewModel::rejectSigning,
                onClose = {
                    if (
                        state.phase == IrohaConnectUiPhase.CLOSED ||
                        state.phase == IrohaConnectUiPhase.ERROR
                    ) {
                        finish()
                    } else {
                        viewModel.closeByUser()
                    }
                },
            )
            if (confirmPin && approvalToken != null) {
                IrohaConnectPinDialog(
                    error = state.authorizationError,
                    onCancel = { confirmPin = false },
                    onConfirm = { pin ->
                        when (state.phase) {
                            IrohaConnectUiPhase.PAIRING -> viewModel.approvePairing(pin, approvalToken)
                            IrohaConnectUiPhase.SIGNING -> viewModel.approveSigning(pin, approvalToken)
                            else -> confirmPin = false
                        }
                    },
                )
            }
            }
        }

        if (savedInstanceState == null || viewModel.state.value.phase == IrohaConnectUiPhase.LOADING) {
            viewModel.start(consumeDeepLink(intent))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!ReleaseSurfacePolicy.irohaConnectAllowed) {
            finish()
            return
        }
        setIntent(intent)
        viewModel.start(consumeDeepLink(intent))
    }

    override fun onStop() {
        if (!isChangingConfigurations && !isFinishing) {
            viewModel.onAppBackgrounded()
        }
        super.onStop()
    }

    private fun consumeDeepLink(source: Intent): String? {
        val value = source.dataString
        source.data = null
        return value
    }

}
