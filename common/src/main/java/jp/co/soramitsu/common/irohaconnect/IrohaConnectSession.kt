package jp.co.soramitsu.common.irohaconnect

import java.net.URI
import java.security.SecureRandom
import jp.co.soramitsu.common.nexus.IrohaAddressCodec

enum class IrohaConnectSigningKind {
    RAW,
    TRANSACTION,
}

data class IrohaConnectPairingReview(
    val appName: String,
    val appUrl: String?,
    val networkName: String,
    val accountNetworkId: String,
    val permissions: IrohaConnectPermissions?,
    val sessionFingerprint: String,
    val expiresAtMillis: Long,
)

data class IrohaConnectSigningReview(
    val requestToken: String,
    val kind: IrohaConnectSigningKind,
    val domainTag: String?,
    val byteLength: Int,
    val payloadPreviewHex: String,
    val expiresAtMillis: Long,
    val readableMessage: String? = null,
)

sealed interface IrohaConnectSessionEvent {
    data class PairingRequested(val review: IrohaConnectPairingReview) : IrohaConnectSessionEvent
    data class SigningRequested(val review: IrohaConnectSigningReview) : IrohaConnectSessionEvent
    data class SendFrame(val bytes: ByteArray, val signed: Boolean = false) : IrohaConnectSessionEvent
    data class DisplayRequested(val title: String, val body: String) : IrohaConnectSessionEvent
    data object ServerEvent : IrohaConnectSessionEvent
    data class Closed(val reason: String) : IrohaConnectSessionEvent
}

class IrohaConnectSessionException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * In-memory wallet-role boundary for one current IrohaConnect v1 session.
 *
 * The caller owns transport and user authentication. This class owns protocol sequencing,
 * ephemeral session keys, permission checks, approval tokens, and request bytes. It deliberately
 * has no retired chain-id or plaintext compatibility path.
 */
class IrohaConnectWalletSession(
    val launch: IrohaConnectLaunch,
    startedAtMillis: Long,
    private val random: SecureRandom = SecureRandom(),
) : AutoCloseable {
    private sealed interface Phase {
        data class AwaitingOpen(val expiresAtMillis: Long) : Phase
        data class AwaitingPairing(
            val open: IrohaConnectControl.Open,
            val expiresAtMillis: Long,
        ) : Phase
        data object AuthorizingPairing : Phase
        data class Active(
            val accountId: String,
            val accountPublicKey: ByteArray,
            val permissions: IrohaConnectPermissions?,
            val directionKeys: IrohaConnectDirectionKeys,
            var nextAppSequence: Long = 2,
            var nextServerSequence: Long = 1,
            var nextWalletSequence: Long = 2,
            var request: PendingRequest? = null,
        ) : Phase
        data object Closed : Phase
    }

    private data class PendingRequest(
        val token: String,
        val kind: IrohaConnectSigningKind,
        val domainTag: String?,
        val message: ByteArray,
        val expiresAtMillis: Long,
        var executing: Boolean = false,
    )

    private var phase: Phase = Phase.AwaitingOpen(safeDeadline(startedAtMillis))

    val isClosed: Boolean
        @Synchronized get() = phase is Phase.Closed

    val approvedAccountId: String?
        @Synchronized get() = (phase as? Phase.Active)?.accountId

    @Synchronized
    fun receive(frameBytes: ByteArray, nowMillis: Long): IrohaConnectSessionEvent {
        return when (val current = phase) {
            is Phase.AwaitingOpen -> receiveOpen(current, frameBytes, nowMillis)
            is Phase.AwaitingPairing,
            Phase.AuthorizingPairing -> throw IrohaConnectSessionException(
                "IrohaConnect sent data before pairing approval completed",
            )
            is Phase.Active -> receiveActive(current, frameBytes, nowMillis)
            Phase.Closed -> throw IrohaConnectSessionException("IrohaConnect session is closed")
        }
    }

    @Synchronized
    fun approvePairing(
        accountId: String,
        nowMillis: Long,
        signer: (ByteArray) -> ByteArray,
    ): IrohaConnectSessionEvent.SendFrame {
        val pending = phase as? Phase.AwaitingPairing
            ?: throw IrohaConnectSessionException("No IrohaConnect pairing approval is pending")
        requireNotExpired(pending.expiresAtMillis, nowMillis, "IrohaConnect pairing approval")
        val parsedAccount = try {
            IrohaAddressCodec.parse(accountId, launch.network.chainDiscriminant)
        } catch (error: Exception) {
            throw IrohaConnectSessionException(
                "The selected account is not a canonical account for ${launch.network.displayName}",
                error,
            )
        }
        val accountPublicKey = IrohaConnectEncoding.hexDecode(parsedAccount.publicKeyHex)
        phase = Phase.AuthorizingPairing

        val ephemeral = IrohaConnectCrypto.ephemeralX25519()
        var directionKeys: IrohaConnectDirectionKeys? = null
        var preimage: ByteArray? = null
        var signature: ByteArray? = null
        var promoted = false
        return try {
            directionKeys = IrohaConnectCrypto.deriveDirectionKeys(
                sid = launch.sid,
                appPublicKey = launch.appPublicKey,
                walletPrivateKey = ephemeral.privateKey,
            )
            preimage = IrohaConnectApprovalPreimage.modern(
                networkIdBytes = launch.networkIdBytes,
                sid = launch.sid,
                appPublicKey = launch.appPublicKey,
                walletPublicKey = ephemeral.publicKey,
                accountId = parsedAccount.address,
                relayToken = launch.relayToken,
                permissions = pending.open.permissions,
            )
            val signingInput = preimage.copyOf()
            signature = try {
                signer(signingInput)
            } finally {
                signingInput.fill(0)
            }
            if (!IrohaConnectCrypto.verifyEd25519(accountPublicKey, preimage, signature)) {
                throw IrohaConnectSessionException(
                    "The pairing signature does not belong to the selected SORA 3 account",
                )
            }
            val frame = IrohaConnectWire.encodeApproval(
                sid = launch.sid,
                sequence = 1,
                walletPublicKey = ephemeral.publicKey,
                accountId = parsedAccount.address,
                permissions = pending.open.permissions,
                signature = signature,
            )
            phase = Phase.Active(
                accountId = parsedAccount.address,
                accountPublicKey = accountPublicKey,
                permissions = pending.open.permissions.copyDeep(),
                directionKeys = directionKeys,
            )
            promoted = true
            IrohaConnectSessionEvent.SendFrame(frame)
        } catch (error: Exception) {
            phase = Phase.Closed
            throw if (error is IrohaConnectSessionException) error else {
                IrohaConnectSessionException("Unable to approve IrohaConnect pairing", error)
            }
        } finally {
            ephemeral.destroy()
            preimage?.fill(0)
            signature?.fill(0)
            if (!promoted) {
                accountPublicKey.fill(0)
                directionKeys?.destroy()
            }
        }
    }

    @Synchronized
    fun rejectPairing(
        reason: String = "Rejected by user",
    ): IrohaConnectSessionEvent.SendFrame {
        if (phase !is Phase.AwaitingPairing) {
            throw IrohaConnectSessionException("No IrohaConnect pairing approval is pending")
        }
        val frame = IrohaConnectWire.encodeReject(
            sid = launch.sid,
            sequence = 1,
            code = 1,
            codeId = "USER_REJECTED",
            reason = boundedReason(reason),
        )
        phase = Phase.Closed
        return IrohaConnectSessionEvent.SendFrame(frame)
    }

    @Synchronized
    fun approveRequest(
        requestToken: String,
        nowMillis: Long,
        signer: (ByteArray) -> ByteArray,
    ): IrohaConnectSessionEvent.SendFrame {
        val active = requireActive()
        val request = requirePendingRequest(active, requestToken)
        request.executing = true
        try {
            if (nowMillis > request.expiresAtMillis) {
                return encryptedError(
                    active,
                    code = "REQUEST_EXPIRED",
                    message = "The IrohaConnect signing approval expired",
                )
            }
            if (IrohaConnectSigningPresentation.readableMessage(request.kind, request.message) == null) {
                return encryptedError(
                    active,
                    code = "UNSUPPORTED_SIGNING_CONTENT",
                    message = "The wallet cannot interpret this request. Nothing was signed.",
                )
            }
            val signingInput = request.message.copyOf()
            val signature = try {
                signer(signingInput)
            } catch (_: Exception) {
                return encryptedError(
                    active,
                    code = "SIGNING_FAILED",
                    message = "The wallet could not sign this request",
                )
            } finally {
                signingInput.fill(0)
            }
            return try {
                if (!IrohaConnectCrypto.verifyEd25519(
                        active.accountPublicKey,
                        request.message,
                        signature,
                    )
                ) {
                    encryptedError(
                        active,
                        code = "SIGNING_FAILED",
                        message = "The signature did not match the approved account",
                    )
                } else {
                    encryptedResult(
                        active,
                        IrohaConnectEnvelopePayload.SignResultOk(
                            IrohaConnectSignature(signature = signature),
                        ),
                        signed = true,
                    )
                }
            } finally {
                signature.fill(0)
            }
        } finally {
            finishRequest(active, request)
        }
    }

    @Synchronized
    fun rejectRequest(
        requestToken: String,
        nowMillis: Long,
    ): IrohaConnectSessionEvent.SendFrame {
        val active = requireActive()
        val request = requirePendingRequest(active, requestToken)
        request.executing = true
        return try {
            encryptedError(
                active,
                code = if (nowMillis > request.expiresAtMillis) "REQUEST_EXPIRED" else "USER_REJECTED",
                message = if (nowMillis > request.expiresAtMillis) {
                    "The IrohaConnect signing approval expired"
                } else {
                    "Rejected by user"
                },
            )
        } finally {
            finishRequest(active, request)
        }
    }

    @Synchronized
    fun closeWithFrame(reason: String = "Wallet closed the connection"): ByteArray? {
        val current = phase
        return try {
            when (current) {
                is Phase.Active -> {
                    val sequence = current.nextWalletSequence
                    val ciphertext = IrohaConnectEnvelopeWire.encryptResult(
                        key = current.directionKeys.walletToApp,
                        sid = launch.sid,
                        sequence = sequence,
                        payload = IrohaConnectEnvelopePayload.Close(boundedReason(reason)),
                    )
                    try {
                        IrohaConnectWire.encodeCiphertext(launch.sid, sequence, ciphertext)
                    } finally {
                        ciphertext.fill(0)
                    }
                }
                else -> null
            }
        } finally {
            destroy(current)
            phase = Phase.Closed
        }
    }

    @Synchronized
    override fun close() {
        destroy(phase)
        phase = Phase.Closed
    }

    private fun receiveOpen(
        current: Phase.AwaitingOpen,
        frameBytes: ByteArray,
        nowMillis: Long,
    ): IrohaConnectSessionEvent.PairingRequested {
        requireNotExpired(current.expiresAtMillis, nowMillis, "IrohaConnect launch")
        val frame = IrohaConnectWire.decodeFrame(frameBytes)
        if (
            !IrohaConnectCrypto.constantTimeEquals(frame.sid, launch.sid) ||
            frame.direction != IrohaConnectDirection.APP_TO_WALLET ||
            frame.sequence != 1L ||
            frame !is IrohaConnectFrame.Control ||
            frame.control !is IrohaConnectControl.Open
        ) {
            throw IrohaConnectSessionException(
                "The first IrohaConnect message must be the launch-bound Open frame",
            )
        }
        val open = frame.control
        if (
            !IrohaConnectCrypto.constantTimeEquals(open.appPublicKey, launch.appPublicKey) ||
            !IrohaConnectCrypto.constantTimeEquals(open.constraint.bytes, launch.networkIdBytes)
        ) {
            throw IrohaConnectSessionException(
                "IrohaConnect Open does not match the scanned session identity",
            )
        }
        validateMetadata(open.appMetadata)
        validatePermissions(open.permissions)
        val expiresAt = safeDeadline(nowMillis)
        phase = Phase.AwaitingPairing(open.copyDeep(), expiresAt)
        val fingerprint = IrohaConnectEncoding.hex(
            IrohaConnectCrypto.blake2b256(launch.sid).copyOfRange(0, 8),
        )
        return IrohaConnectSessionEvent.PairingRequested(
            IrohaConnectPairingReview(
                appName = open.appMetadata?.name ?: "Unnamed application",
                appUrl = open.appMetadata?.url,
                networkName = launch.network.displayName,
                accountNetworkId = launch.network.id.wireId,
                permissions = open.permissions.copyDeep(),
                sessionFingerprint = fingerprint,
                expiresAtMillis = expiresAt,
            ),
        )
    }

    private fun receiveActive(
        active: Phase.Active,
        frameBytes: ByteArray,
        nowMillis: Long,
    ): IrohaConnectSessionEvent {
        val frame = IrohaConnectWire.decodeFrame(frameBytes)
        if (
            !IrohaConnectCrypto.constantTimeEquals(frame.sid, launch.sid) ||
            frame.direction != IrohaConnectDirection.APP_TO_WALLET
        ) {
            throw IrohaConnectSessionException("IrohaConnect frame belongs to another session")
        }
        if (frame is IrohaConnectFrame.Control && frame.control is IrohaConnectControl.ServerEvent) {
            if (frame.sequence != active.nextServerSequence) {
                throw IrohaConnectSessionException("IrohaConnect server-event sequence is invalid")
            }
            active.nextServerSequence = incrementSequence(active.nextServerSequence)
            return IrohaConnectSessionEvent.ServerEvent
        }
        if (frame.sequence != active.nextAppSequence) {
            throw IrohaConnectSessionException("IrohaConnect application sequence is invalid")
        }
        active.nextAppSequence = incrementSequence(active.nextAppSequence)

        return when (frame) {
            is IrohaConnectFrame.Control -> when (val control = frame.control) {
                is IrohaConnectControl.Ping -> {
                    val sequence = active.nextWalletSequence
                    val response = IrohaConnectWire.encodePong(launch.sid, sequence, control.nonce)
                    active.nextWalletSequence = incrementSequence(sequence)
                    IrohaConnectSessionEvent.SendFrame(response)
                }
                is IrohaConnectControl.Close -> {
                    destroy(active)
                    phase = Phase.Closed
                    IrohaConnectSessionEvent.Closed(control.reason)
                }
                else -> throw IrohaConnectSessionException(
                    "Unexpected cleartext IrohaConnect control after approval",
                )
            }
            is IrohaConnectFrame.Ciphertext -> {
                if (frame.ciphertextDirection != IrohaConnectDirection.APP_TO_WALLET) {
                    throw IrohaConnectSessionException("IrohaConnect ciphertext direction is invalid")
                }
                val envelope = try {
                    IrohaConnectEnvelopeWire.decrypt(
                        key = active.directionKeys.appToWallet,
                        sid = launch.sid,
                        direction = frame.direction,
                        sequence = frame.sequence,
                        aead = frame.aead,
                    )
                } catch (error: Exception) {
                    throw IrohaConnectSessionException("IrohaConnect ciphertext authentication failed", error)
                }
                processEnvelope(active, envelope.payload, nowMillis)
            }
        }
    }

    private fun processEnvelope(
        active: Phase.Active,
        payload: IrohaConnectEnvelopePayload,
        nowMillis: Long,
    ): IrohaConnectSessionEvent = when (payload) {
        is IrohaConnectEnvelopePayload.SignRaw -> prepareRequest(
            active,
            IrohaConnectSigningKind.RAW,
            payload.domainTag,
            payload.message,
            nowMillis,
        )
        is IrohaConnectEnvelopePayload.SignTransaction -> prepareRequest(
            active,
            IrohaConnectSigningKind.TRANSACTION,
            null,
            payload.transaction,
            nowMillis,
        )
        is IrohaConnectEnvelopePayload.Display -> IrohaConnectSessionEvent.DisplayRequested(
            payload.title,
            payload.body,
        )
        is IrohaConnectEnvelopePayload.Close -> {
            destroy(active)
            phase = Phase.Closed
            IrohaConnectSessionEvent.Closed(payload.reason)
        }
        is IrohaConnectEnvelopePayload.Reject -> {
            destroy(active)
            phase = Phase.Closed
            IrohaConnectSessionEvent.Closed(payload.reason)
        }
        is IrohaConnectEnvelopePayload.SignResultOk,
        is IrohaConnectEnvelopePayload.SignResultError -> throw IrohaConnectSessionException(
            "Application sent a wallet-only IrohaConnect result",
        )
    }

    private fun prepareRequest(
        active: Phase.Active,
        kind: IrohaConnectSigningKind,
        domainTag: String?,
        input: ByteArray,
        nowMillis: Long,
    ): IrohaConnectSessionEvent {
        if (active.request != null) {
            input.fill(0)
            return encryptedError(
                active,
                code = "REQUEST_IN_FLIGHT",
                message = "Finish the current approval before sending another request",
            )
        }
        val permissionError = signingPermissionError(active.permissions, kind, domainTag)
        if (permissionError != null) {
            input.fill(0)
            return encryptedError(active, "PERMISSION_DENIED", permissionError)
        }
        if (input.isEmpty()) {
            input.fill(0)
            return encryptedError(active, "INVALID_REQUEST", "Signing request is empty")
        }
        val token = randomToken()
        val request = PendingRequest(
            token = token,
            kind = kind,
            domainTag = domainTag,
            message = input,
            expiresAtMillis = safeDeadline(nowMillis),
        )
        active.request = request
        return IrohaConnectSessionEvent.SigningRequested(
            IrohaConnectSigningReview(
                requestToken = token,
                kind = kind,
                domainTag = domainTag,
                byteLength = input.size,
                payloadPreviewHex = IrohaConnectEncoding.hex(input.copyOfRange(0, minOf(96, input.size))),
                expiresAtMillis = request.expiresAtMillis,
                readableMessage = IrohaConnectSigningPresentation.readableMessage(kind, input),
            ),
        )
    }

    private fun encryptedError(
        active: Phase.Active,
        code: String,
        message: String,
    ): IrohaConnectSessionEvent.SendFrame = encryptedResult(
        active,
        IrohaConnectEnvelopePayload.SignResultError(code, boundedReason(message)),
        signed = false,
    )

    private fun encryptedResult(
        active: Phase.Active,
        payload: IrohaConnectEnvelopePayload,
        signed: Boolean,
    ): IrohaConnectSessionEvent.SendFrame {
        val sequence = active.nextWalletSequence
        val ciphertext = IrohaConnectEnvelopeWire.encryptResult(
            key = active.directionKeys.walletToApp,
            sid = launch.sid,
            sequence = sequence,
            payload = payload,
        )
        val frame = try {
            IrohaConnectWire.encodeCiphertext(launch.sid, sequence, ciphertext)
        } finally {
            ciphertext.fill(0)
        }
        active.nextWalletSequence = incrementSequence(sequence)
        return IrohaConnectSessionEvent.SendFrame(frame, signed)
    }

    private fun requirePendingRequest(
        active: Phase.Active,
        requestToken: String,
    ): PendingRequest {
        val request = active.request
            ?: throw IrohaConnectSessionException("No IrohaConnect signing request is pending")
        if (
            request.executing ||
            !IrohaConnectCrypto.constantTimeEquals(
                request.token.toByteArray(Charsets.UTF_8),
                requestToken.toByteArray(Charsets.UTF_8),
            )
        ) {
            throw IrohaConnectSessionException("IrohaConnect signing approval token is stale")
        }
        return request
    }

    private fun finishRequest(active: Phase.Active, request: PendingRequest) {
        if (active.request === request) active.request = null
        request.message.fill(0)
    }

    private fun requireActive(): Phase.Active = phase as? Phase.Active
        ?: throw IrohaConnectSessionException("IrohaConnect session is not approved")

    private fun destroy(value: Phase) {
        if (value is Phase.Active) {
            value.request?.message?.fill(0)
            value.request = null
            value.accountPublicKey.fill(0)
            value.directionKeys.destroy()
        }
    }

    private fun validateMetadata(metadata: IrohaConnectAppMetadata?) {
        metadata ?: return
        if (
            metadata.name.isBlank() ||
            metadata.name != metadata.name.trim() ||
            metadata.name.toByteArray().size > 128 ||
            metadata.name.any(Char::isISOControl)
        ) {
            throw IrohaConnectSessionException("IrohaConnect application name is invalid")
        }
        metadata.url?.let { raw ->
            val uri = runCatching { URI(raw) }.getOrNull()
            if (
                uri == null ||
                uri.scheme != "https" ||
                uri.host.isNullOrBlank() ||
                uri.userInfo != null
            ) {
                throw IrohaConnectSessionException("IrohaConnect application URL is invalid")
            }
        }
        metadata.iconHash?.let {
            if (!it.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                throw IrohaConnectSessionException("IrohaConnect icon hash is invalid")
            }
        }
    }

    private fun validatePermissions(permissions: IrohaConnectPermissions?) {
        permissions ?: return
        if (
            permissions.methods.size > 16 ||
            permissions.events.size > 16 ||
            permissions.resources.orEmpty().size > 64 ||
            permissions.methods.distinct().size != permissions.methods.size ||
            permissions.events.distinct().size != permissions.events.size ||
            permissions.resources?.distinct()?.size != permissions.resources?.size
        ) {
            throw IrohaConnectSessionException("IrohaConnect permissions are not canonical")
        }
        val supportedMethods = setOf(METHOD_SIGN_RAW, METHOD_SIGN_TRANSACTION)
        if (permissions.methods.any { it !in supportedMethods } || permissions.events.isNotEmpty()) {
            throw IrohaConnectSessionException("IrohaConnect requested unsupported permissions")
        }
        permissions.resources.orEmpty().forEach {
            if (
                it.isBlank() ||
                it != it.trim() ||
                it.toByteArray().size > 256 ||
                it.any(Char::isISOControl)
            ) {
                throw IrohaConnectSessionException("IrohaConnect resource permission is invalid")
            }
        }
        if (METHOD_SIGN_RAW in permissions.methods && permissions.resources.isNullOrEmpty()) {
            throw IrohaConnectSessionException("Raw signing requires explicit domain resources")
        }
        if (METHOD_SIGN_TRANSACTION in permissions.methods && permissions.resources != null) {
            throw IrohaConnectSessionException(
                "Transaction signing cannot be safely scoped by a resource selector",
            )
        }
    }

    private fun signingPermissionError(
        permissions: IrohaConnectPermissions?,
        kind: IrohaConnectSigningKind,
        domainTag: String?,
    ): String? = when (kind) {
        IrohaConnectSigningKind.RAW -> when {
            permissions == null || METHOD_SIGN_RAW !in permissions.methods ->
                "Pairing did not grant raw signing"
            domainTag.isNullOrBlank() || domainTag !in permissions.resources.orEmpty() ->
                "Pairing did not grant this raw-signing domain"
            else -> null
        }
        IrohaConnectSigningKind.TRANSACTION -> when {
            permissions == null || METHOD_SIGN_TRANSACTION !in permissions.methods ->
                "Pairing did not grant transaction signing"
            permissions.resources != null ->
                "Transaction signing permission has an invalid resource scope"
            else -> null
        }
    }

    private fun IrohaConnectControl.Open.copyDeep() = copy(
        appPublicKey = appPublicKey.copyOf(),
        appMetadata = appMetadata?.copy(),
        constraint = IrohaConnectConstraint.NetworkId(constraint.bytes.copyOf()),
        permissions = permissions.copyDeep(),
    )

    private fun IrohaConnectPermissions?.copyDeep(): IrohaConnectPermissions? = this?.copy(
        methods = methods.toList(),
        events = events.toList(),
        resources = resources?.toList(),
    )

    private fun randomToken(): String = ByteArray(32)
        .also(random::nextBytes)
        .let(IrohaConnectEncoding::base64Url)

    private fun boundedReason(value: String): String = value
        .trim()
        .take(MAX_REASON_CHARACTERS)
        .ifBlank { "IrohaConnect request was declined" }

    private fun requireNotExpired(deadline: Long, now: Long, label: String) {
        if (now > deadline) {
            close()
            throw IrohaConnectSessionException("$label expired")
        }
    }

    private fun safeDeadline(now: Long): Long =
        if (now > Long.MAX_VALUE - APPROVAL_TTL_MILLIS) Long.MAX_VALUE else now + APPROVAL_TTL_MILLIS

    private fun incrementSequence(sequence: Long): Long {
        if (sequence == Long.MAX_VALUE) {
            close()
            throw IrohaConnectSessionException("IrohaConnect sequence space is exhausted")
        }
        return sequence + 1
    }

    private companion object {
        const val APPROVAL_TTL_MILLIS = 2 * 60_000L
        const val MAX_REASON_CHARACTERS = 240
        const val METHOD_SIGN_RAW = "sign_raw"
        const val METHOD_SIGN_TRANSACTION = "sign_transaction"
    }
}
