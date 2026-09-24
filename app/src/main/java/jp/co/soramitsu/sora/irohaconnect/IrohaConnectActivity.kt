package jp.co.soramitsu.sora.irohaconnect

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme

@AndroidEntryPoint
class IrohaConnectActivity : AppCompatActivity() {
    private enum class Authorization {
        PAIR,
        SIGN,
    }

    @Inject lateinit var darkThemeManager: DarkThemeManager

    private val viewModel: IrohaConnectViewModel by viewModels()
    private var authorizationInProgress = false
    private var pendingAuthorization: Authorization? = null

    private val biometricPrompt by lazy {
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    super.onAuthenticationSucceeded(result)
                    authorizationInProgress = false
                    val authorization = pendingAuthorization.also { pendingAuthorization = null }
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        viewModel.onAppBackgrounded()
                        return
                    }
                    when (authorization) {
                        Authorization.PAIR -> viewModel.approvePairingAfterAuthentication()
                        Authorization.SIGN -> viewModel.approveSigningAfterAuthentication()
                        null -> Unit
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    authorizationInProgress = false
                    pendingAuthorization = null
                    if (errorCode in terminalAuthenticationErrors) {
                        viewModel.authorizationUnavailable()
                    } else if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        viewModel.onAppBackgrounded()
                    }
                }
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            SoraAppTheme(darkTheme = darkTheme) {
            IrohaConnectScreen(
                state = state,
                onApprovePairing = { authenticate(Authorization.PAIR) },
                onRejectPairing = viewModel::rejectPairing,
                onApproveSigning = { authenticate(Authorization.SIGN) },
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
            }
        }

        if (savedInstanceState == null || viewModel.state.value.phase == IrohaConnectUiPhase.LOADING) {
            viewModel.start(consumeDeepLink(intent))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (authorizationInProgress) biometricPrompt.cancelAuthentication()
        setIntent(intent)
        authorizationInProgress = false
        pendingAuthorization = null
        viewModel.start(consumeDeepLink(intent))
    }

    override fun onStop() {
        if (!isChangingConfigurations && !isFinishing && !authorizationInProgress) {
            viewModel.onAppBackgrounded()
        }
        super.onStop()
    }

    private fun authenticate(authorization: Authorization) {
        if (authorizationInProgress) return
        val expectedPhase = when (authorization) {
            Authorization.PAIR -> IrohaConnectUiPhase.PAIRING
            Authorization.SIGN -> IrohaConnectUiPhase.SIGNING
        }
        if (viewModel.state.value.phase != expectedPhase) return

        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            viewModel.authorizationUnavailable()
            return
        }

        authorizationInProgress = true
        pendingAuthorization = authorization
        val prompt = BiometricPrompt.PromptInfo.Builder()
            .setTitle(
                when (authorization) {
                    Authorization.PAIR -> "Approve encrypted pairing"
                    Authorization.SIGN -> "Approve this signature"
                },
            )
            .setSubtitle("SORA 3 • IrohaConnect")
            .setDescription(
                when (authorization) {
                    Authorization.PAIR -> "Confirm the application and selected account"
                    Authorization.SIGN -> "Confirm this one request; pairing never signs automatically"
                },
            )
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(true)
            .build()
        biometricPrompt.authenticate(prompt)
    }

    private fun consumeDeepLink(source: Intent): String? {
        val value = source.dataString
        source.data = null
        return value
    }

    private companion object {
        val terminalAuthenticationErrors = setOf(
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
        )
    }
}
