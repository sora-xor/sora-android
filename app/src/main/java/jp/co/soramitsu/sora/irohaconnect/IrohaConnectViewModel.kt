package jp.co.soramitsu.sora.irohaconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.irohaconnect.IrohaConnectCrypto
import jp.co.soramitsu.common.irohaconnect.IrohaConnectLaunch
import jp.co.soramitsu.common.irohaconnect.IrohaConnectPairingReview
import jp.co.soramitsu.common.irohaconnect.IrohaConnectSessionEvent
import jp.co.soramitsu.common.irohaconnect.IrohaConnectSigningReview
import jp.co.soramitsu.common.irohaconnect.IrohaConnectUri
import jp.co.soramitsu.common.irohaconnect.IrohaConnectWalletSession
import jp.co.soramitsu.common.nexus.IrohaAddressCodec
import jp.co.soramitsu.common.nexus.IrohaKeyDerivation
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class IrohaConnectUiPhase {
    LOADING,
    CONNECTING,
    WAITING_FOR_APP,
    PAIRING,
    AUTHORIZING,
    CONNECTED,
    SIGNING,
    CLOSED,
    ERROR,
}

data class IrohaConnectUiState(
    val phase: IrohaConnectUiPhase = IrohaConnectUiPhase.LOADING,
    val accountName: String = "",
    val accountAddress: String = "",
    val networkName: String = "SORA 3",
    val pairing: IrohaConnectPairingReview? = null,
    val signing: IrohaConnectSigningReview? = null,
    val appName: String = "",
    val message: String = "",
    val error: String? = null,
)

@HiltViewModel
class IrohaConnectViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val database: AppDatabase,
    private val transportFactory: IrohaConnectTransportFactory,
    private val resourceManager: jp.co.soramitsu.androidfoundation.resource.ResourceManager,
) : ViewModel() {
    private data class ActiveAccount(
        val walletId: String,
        val displayName: String,
        val networkAccount: NetworkAccountLocal,
    )

    private val protocolMutex = Mutex()
    private val _state = MutableStateFlow(IrohaConnectUiState())
    val state: StateFlow<IrohaConnectUiState> = _state.asStateFlow()

    private var generation = 0L
    private var launch: IrohaConnectLaunch? = null
    private var walletSession: IrohaConnectWalletSession? = null
    private var transport: IrohaConnectTransport? = null
    private var activeAccount: ActiveAccount? = null
    private var accountWatcher: Job? = null
    private var expiryJob: Job? = null

    fun start(rawUri: String?) {
        val attempt = ++generation
        viewModelScope.launch {
            protocolMutex.withLock {
                dispose(sendClose = true)
                _state.value = IrohaConnectUiState()
                if (rawUri.isNullOrBlank()) {
                    fail(resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_empty))
                    return@withLock
                }
                try {
                    val parsed = withContext(Dispatchers.Default) { IrohaConnectUri.parse(rawUri) }
                    val account = withContext(Dispatchers.IO) { loadActiveAccount(parsed) }
                    if (attempt != generation) return@withLock
                    launch = parsed
                    activeAccount = account
                    walletSession = IrohaConnectWalletSession(parsed, System.currentTimeMillis())
                    _state.value = IrohaConnectUiState(
                        phase = IrohaConnectUiPhase.CONNECTING,
                        accountName = account.displayName,
                        accountAddress = account.networkAccount.address,
                        networkName = parsed.network.displayName,
                        message = resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_opening_relay, parsed.network.displayName),
                    )
                    transport = transportFactory.connect(parsed, listenerFor(attempt))
                    watchSelectedAccount(attempt, account.walletId)
                    scheduleExpiry(attempt, OPEN_TTL_MILLIS, IrohaConnectUiPhase.WAITING_FOR_APP)
                } catch (_: Exception) {
                    if (attempt == generation) {
                        dispose(sendClose = false)
                        fail(
                            resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_cannot_open),
                        )
                    }
                }
            }
        }
    }

    fun approvePairingAfterAuthentication() {
        val attempt = generation
        viewModelScope.launch {
            protocolMutex.withLock {
                if (attempt != generation || _state.value.phase != IrohaConnectUiPhase.PAIRING) {
                    return@withLock
                }
                val session = walletSession ?: return@withLock
                _state.value = _state.value.copy(
                    phase = IrohaConnectUiPhase.AUTHORIZING,
                    message = resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_verifying),
                )
                try {
                    val event = withVerifiedDerivedAccount { derived ->
                        session.approvePairing(
                            accountId = derived.address,
                            nowMillis = System.currentTimeMillis(),
                        ) { message ->
                            IrohaConnectCrypto.signEd25519(derived.privateKeySeed, message)
                        }
                    }
                    send(event)
                    expiryJob?.cancel()
                    _state.value = _state.value.copy(
                        phase = IrohaConnectUiPhase.CONNECTED,
                        pairing = null,
                        message = resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_active),
                    )
                } catch (_: Exception) {
                    dispose(sendClose = false)
                    fail(resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_pairing_failed))
                }
            }
        }
    }

    fun rejectPairing() {
        viewModelScope.launch {
            protocolMutex.withLock {
                val session = walletSession ?: return@withLock
                runCatching { send(session.rejectPairing()) }
                dispose(sendClose = false)
                _state.value = _state.value.copy(
                    phase = IrohaConnectUiPhase.CLOSED,
                    pairing = null,
                    message = resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_declined),
                )
            }
        }
    }

    fun approveSigningAfterAuthentication() {
        val attempt = generation
        viewModelScope.launch {
            protocolMutex.withLock {
                val review = _state.value.signing
                if (
                    attempt != generation ||
                    _state.value.phase != IrohaConnectUiPhase.SIGNING ||
                    review == null || review.readableMessage == null
                ) {
                    return@withLock
                }
                val session = walletSession ?: return@withLock
                _state.value = _state.value.copy(
                    phase = IrohaConnectUiPhase.AUTHORIZING,
                    message = resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_signing),
                )
                try {
                    val response = withVerifiedDerivedAccount { derived ->
                        session.approveRequest(
                            requestToken = review.requestToken,
                            nowMillis = System.currentTimeMillis(),
                        ) { message ->
                            IrohaConnectCrypto.signEd25519(derived.privateKeySeed, message)
                        }
                    }
                    send(response)
                    expiryJob?.cancel()
                    _state.value = _state.value.copy(
                        phase = IrohaConnectUiPhase.CONNECTED,
                        signing = null,
                        message = if (response.signed) {
                            resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_signed)
                        } else {
                            resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_sign_failed)
                        },
                    )
                } catch (_: Exception) {
                    dispose(sendClose = true)
                    fail(resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_identity_changed))
                }
            }
        }
    }

    fun rejectSigning() {
        viewModelScope.launch {
            protocolMutex.withLock {
                val review = _state.value.signing ?: return@withLock
                val session = walletSession ?: return@withLock
                runCatching {
                    send(session.rejectRequest(review.requestToken, System.currentTimeMillis()))
                }.onFailure {
                    dispose(sendClose = false)
                    fail(resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_decline_failed))
                    return@withLock
                }
                expiryJob?.cancel()
                _state.value = _state.value.copy(
                    phase = IrohaConnectUiPhase.CONNECTED,
                    signing = null,
                    message = resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_sign_declined),
                )
            }
        }
    }

    fun closeByUser() {
        viewModelScope.launch {
            protocolMutex.withLock {
                dispose(sendClose = true)
                _state.value = _state.value.copy(
                    phase = IrohaConnectUiPhase.CLOSED,
                    pairing = null,
                    signing = null,
                    message = resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_session_closed),
                )
            }
        }
    }

    fun onAppBackgrounded() {
        val attempt = generation
        viewModelScope.launch {
            protocolMutex.withLock {
                if (attempt != generation || walletSession == null) return@withLock
                dispose(sendClose = true)
                _state.value = _state.value.copy(
                    phase = IrohaConnectUiPhase.CLOSED,
                    pairing = null,
                    signing = null,
                    message = resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_background_closed),
                )
            }
        }
    }

    fun authorizationUnavailable() {
        val attempt = generation
        viewModelScope.launch {
            protocolMutex.withLock {
                if (
                    attempt != generation ||
                    _state.value.phase !in setOf(
                        IrohaConnectUiPhase.PAIRING,
                        IrohaConnectUiPhase.SIGNING,
                    )
                ) {
                    return@withLock
                }
                dispose(sendClose = true)
                fail(
                    resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_lock_required),
                )
            }
        }
    }

    private fun listenerFor(attempt: Long) = object : IrohaConnectTransportListener {
        override fun onConnected() {
            viewModelScope.launch {
                protocolMutex.withLock {
                    if (attempt != generation || walletSession == null) return@withLock
                    _state.value = _state.value.copy(
                        phase = IrohaConnectUiPhase.WAITING_FOR_APP,
                        message = resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_relay_ready),
                    )
                }
            }
        }

        override fun onBinaryMessage(bytes: ByteArray) {
            viewModelScope.launch {
                protocolMutex.withLock {
                    try {
                        if (attempt != generation) return@withLock
                        val event = walletSession?.receive(bytes, System.currentTimeMillis())
                            ?: return@withLock
                        handle(event, attempt)
                    } catch (_: Exception) {
                        dispose(sendClose = false)
                        fail(resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_invalid_message))
                    } finally {
                        bytes.fill(0)
                    }
                }
            }
        }

        override fun onClosed(reason: String) {
            viewModelScope.launch {
                protocolMutex.withLock {
                    if (attempt != generation || walletSession == null) return@withLock
                    dispose(sendClose = false)
                    _state.value = _state.value.copy(
                        phase = IrohaConnectUiPhase.CLOSED,
                        pairing = null,
                        signing = null,
                        message = reason.ifBlank { resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_app_closed) },
                    )
                }
            }
        }

        override fun onFailure() {
            viewModelScope.launch {
                protocolMutex.withLock {
                    if (attempt != generation || walletSession == null) return@withLock
                    dispose(sendClose = false)
                    fail(resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_interrupted))
                }
            }
        }
    }

    private fun handle(event: IrohaConnectSessionEvent, attempt: Long) {
        when (event) {
            is IrohaConnectSessionEvent.PairingRequested -> {
                expiryJob?.cancel()
                _state.value = _state.value.copy(
                    phase = IrohaConnectUiPhase.PAIRING,
                    pairing = event.review,
                    appName = event.review.appName,
                    message = resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_review_app),
                )
                scheduleExpiryAt(attempt, event.review.expiresAtMillis, IrohaConnectUiPhase.PAIRING)
            }
            is IrohaConnectSessionEvent.SigningRequested -> {
                _state.value = _state.value.copy(
                    phase = IrohaConnectUiPhase.SIGNING,
                    signing = event.review,
                    message = resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_review_scope),
                )
                scheduleExpiryAt(attempt, event.review.expiresAtMillis, IrohaConnectUiPhase.SIGNING)
            }
            is IrohaConnectSessionEvent.SendFrame -> send(event)
            is IrohaConnectSessionEvent.DisplayRequested -> {
                _state.value = _state.value.copy(
                    message = "${event.title.take(80)} — ${event.body.take(160)}",
                )
            }
            IrohaConnectSessionEvent.ServerEvent -> Unit
            is IrohaConnectSessionEvent.Closed -> {
                dispose(sendClose = false)
                _state.value = _state.value.copy(
                    phase = IrohaConnectUiPhase.CLOSED,
                    pairing = null,
                    signing = null,
                    message = event.reason.ifBlank { resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_app_closed_short) },
                )
            }
        }
    }

    private fun send(event: IrohaConnectSessionEvent.SendFrame) {
        try {
            transport?.send(event.bytes)
                ?: throw IllegalStateException("IrohaConnect transport is unavailable")
        } finally {
            event.bytes.fill(0)
        }
    }

    private suspend fun loadActiveAccount(parsed: IrohaConnectLaunch): ActiveAccount =
        userRepository.withWalletMutationLocked { snapshot ->
            val selected = snapshot.selectedAccount
                ?: throw IllegalStateException("No selected wallet")
            loadActiveAccountLocked(parsed, selected)
        }

    private suspend fun loadActiveAccountLocked(
        parsed: IrohaConnectLaunch,
        selected: SoraAccount,
    ): ActiveAccount {
        val dao = database.walletIdentityDao()
        val identity = dao.getWallet(selected.substrateAddress)
            ?: throw IllegalStateException("Wallet identity is missing")
        if (identity.migrationState != VERIFIED_STATE || identity.derivationVersion != DERIVATION_VERSION) {
            throw IllegalStateException("Wallet identity is not verified")
        }
        val journal = dao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        if (
            journal?.state != VERIFIED_STATE ||
            journal.selectedWalletId != selected.substrateAddress ||
            journal.failureCode != null ||
            dao.getActiveDeletionOperation() != null
        ) {
            throw IllegalStateException("Wallet migration is not verified")
        }
        val networkAccount = dao.getNetworkAccount(
            selected.substrateAddress,
            parsed.network.id.wireId,
        ) ?: throw IllegalStateException("SORA 3 network account is missing")
        if (
            !networkAccount.enabled ||
            networkAccount.derivationVersion != DERIVATION_VERSION ||
            networkAccount.derivationPath != parsed.network.derivationPath
        ) {
            throw IllegalStateException("SORA 3 network account is disabled or stale")
        }
        val parsedAddress = IrohaAddressCodec.parse(
            networkAccount.address,
            parsed.network.chainDiscriminant,
        )
        if (normalizeHex(networkAccount.publicKey) != parsedAddress.publicKeyHex.lowercase()) {
            throw IllegalStateException("SORA 3 address and public key do not match")
        }
        return ActiveAccount(
            walletId = selected.substrateAddress,
            displayName = selected.accountTitle(),
            networkAccount = networkAccount,
        )
    }

    private suspend fun <T> withVerifiedDerivedAccount(
        block: (IrohaKeyDerivation.DerivedAccount) -> T,
    ): T {
        val parsed = launch ?: throw IllegalStateException("IrohaConnect launch is missing")
        val expected = activeAccount ?: throw IllegalStateException("Selected account is missing")
        return withContext(Dispatchers.IO) {
            userRepository.withWalletMutationLocked { snapshot ->
                val selected = snapshot.selectedAccount
                    ?: throw IllegalStateException("Selected wallet disappeared")
                if (selected.substrateAddress != expected.walletId) {
                    throw IllegalStateException("Selected wallet changed")
                }
                val current = loadActiveAccountLocked(parsed, selected)
                if (current.networkAccount != expected.networkAccount) {
                    throw IllegalStateException("SORA 3 account changed")
                }
                if (credentialsRepository.isExplicitWatchOnly(selected)) {
                    throw IllegalStateException("Watch-only wallets cannot sign")
                }
                val mnemonic = credentialsRepository.retrieveMnemonic(selected)
                val derived = IrohaKeyDerivation.derive(mnemonic, parsed.network)
                try {
                    if (
                        derived.network != parsed.network.id ||
                        derived.derivationPath != current.networkAccount.derivationPath ||
                        derived.address != current.networkAccount.address ||
                        normalizeHex(current.networkAccount.publicKey) !=
                        derived.publicKey.toHex()
                    ) {
                        throw IllegalStateException("Derived SORA 3 account does not match storage")
                    }
                    block(derived)
                } finally {
                    derived.destroy()
                }
            }
        }
    }

    private fun watchSelectedAccount(attempt: Long, walletId: String) {
        accountWatcher?.cancel()
        accountWatcher = viewModelScope.launch {
            userRepository.flowCurSoraAccount()
                .map { account -> account.substrateAddress }
                .distinctUntilChanged()
                .collect { selectedId ->
                    if (attempt == generation && selectedId != walletId) {
                        protocolMutex.withLock {
                            if (attempt == generation && walletSession != null) {
                                dispose(sendClose = true)
                                fail(resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_wallet_changed))
                            }
                        }
                    }
                }
        }
    }

    private fun scheduleExpiry(
        attempt: Long,
        delayMillis: Long,
        expectedPhase: IrohaConnectUiPhase,
    ) = scheduleExpiryAt(attempt, System.currentTimeMillis() + delayMillis, expectedPhase)

    private fun scheduleExpiryAt(
        attempt: Long,
        expiresAtMillis: Long,
        expectedPhase: IrohaConnectUiPhase,
    ) {
        expiryJob?.cancel()
        expiryJob = viewModelScope.launch {
            delay((expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(1))
            protocolMutex.withLock {
                if (attempt == generation && _state.value.phase == expectedPhase) {
                    dispose(sendClose = true)
                    _state.value = _state.value.copy(
                        phase = IrohaConnectUiPhase.CLOSED,
                        pairing = null,
                        signing = null,
                        message = resourceManager.getString(jp.co.soramitsu.common.R.string.wallet_connect_expired),
                    )
                }
            }
        }
    }

    private fun dispose(sendClose: Boolean) {
        expiryJob?.cancel()
        expiryJob = null
        accountWatcher?.cancel()
        accountWatcher = null
        val currentSession = walletSession
        val currentTransport = transport
        if (sendClose && currentSession != null && currentTransport != null) {
            currentSession.closeWithFrame()?.let { frame ->
                try {
                    currentTransport.send(frame)
                } catch (_: Exception) {
                    // Closing remains local and fail-closed when the relay is already gone.
                } finally {
                    frame.fill(0)
                }
            }
        }
        currentSession?.close()
        currentTransport?.close()
        walletSession = null
        transport = null
        launch = null
        activeAccount = null
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(
            phase = IrohaConnectUiPhase.ERROR,
            pairing = null,
            signing = null,
            message = message,
            error = message,
        )
    }

    override fun onCleared() {
        dispose(sendClose = true)
        super.onCleared()
    }

    private fun normalizeHex(value: String): String =
        value.removePrefix("0x").removePrefix("0X").lowercase().also {
            require(it.matches(Regex("^[0-9a-f]{64}$")))
        }

    private fun ByteArray.toHex(): String = joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private companion object {
        const val VERIFIED_STATE = "VERIFIED"
        const val DERIVATION_VERSION = 1
        const val OPEN_TTL_MILLIS = 2 * 60_000L
    }
}
