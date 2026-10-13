package jp.co.soramitsu.feature_wallet_impl.data.nexus

import java.security.SecureRandom
import jp.co.soramitsu.common.nexus.*
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Local testnet signing. Only unsigned drafts are cached; every key is derived, checked and wiped. */
class TairaTransactionSigner(
    private val credentials: CredentialsRepository,
    private val users: UserRepository,
    private val client: TairaSigningClient = TairaSigningClient(),
    private val now: () -> Long = System::currentTimeMillis,
) : NexusTransactionSigner {
    private data class Cached(val draft: TairaSigningClient.Draft, val quote: TairaSigningClient.Quote)
    private val mutex = Mutex()
    private val drafts = linkedMapOf<NexusTransferSigningRequest, Cached>()
    private val random = SecureRandom()

    override fun isQualifiedFor(network: NexusNetwork): Boolean = network == NexusNetworks.taira

    override suspend fun quote(request: NexusTransferSigningRequest): NexusTransferFeeQuote = mutex.withLock {
        requireRequest(request)
        drafts.entries.removeAll { now() >= it.value.draft.createdAt + 80_000 }
        val draft = drafts[request]?.draft ?: TairaSigningClient.Draft(
            authority = request.account.address, recipient = request.recipient,
            authorityKey = TairaTransferCodec.unhex(request.account.publicKey),
            recipientKey = TairaTransferCodec.unhex(IrohaAddressCodec.parse(request.recipient, request.network.chainDiscriminant).publicKeyHex),
            amount = request.amount, createdAt = now(), nonce = (random.nextInt().toLong() and 0xffffffffL).coerceAtLeast(1),
        )
        val derived = derive(request)
        val quote = try {
            client.quote(draft) { message ->
                try { TairaTransferCodec.sign(derived.privateKeySeed, message) }
                finally { derived.destroy() }
            }
        } finally { derived.destroy() }
        if (drafts.size >= 8 && request !in drafts) drafts.remove(drafts.keys.first())
        drafts[request] = Cached(draft, quote)
        quoteFor(request, quote)
    }

    override suspend fun sign(request: NexusTransferSigningRequest, quote: NexusTransferFeeQuote): NexusSignedTransaction = mutex.withLock {
        requireRequest(request)
        val cached = checkNotNull(drafts.remove(request)) { "TAIRA_QUOTE_MISSING" }
        cached.draft.requireFresh(now())
        check(quoteFor(request, cached.quote) == quote) { "TAIRA_QUOTE_CHANGED" }
        val derived = derive(request)
        try {
            val payload = cached.quote.payload
            val signature = TairaTransferCodec.sign(derived.privateKeySeed, TairaTransferCodec.signingMessage(payload))
            NexusSignedTransaction(TairaTransferCodec.envelope(payload, signature, derived.publicKey), TairaTransferCodec.transactionHash(payload))
        } finally { derived.destroy() }
    }

    private fun quoteFor(request: NexusTransferSigningRequest, quote: TairaSigningClient.Quote) = NexusTransferFeeQuote(
        networkId = request.network.id.wireId, authority = request.account.address,
        recipient = request.recipient, assetDefinitionId = request.assetDefinitionId, amount = request.amount,
        fee = quote.total, quoteIdentity = quote.identity, validUntilBlock = null,
    )

    private fun requireRequest(request: NexusTransferSigningRequest) {
        check(isQualifiedFor(request.network)) { "TAIRA_SIGNER_WRONG_NETWORK" }
        check(request.account.networkId == WalletNetworkId.TAIRA.wireId && request.account.enabled &&
            request.account.derivationVersion == 1 && request.account.derivationPath == NexusNetworks.taira.derivationPath)
        check(request.assetDefinitionId == TairaTestnetContract.XOR_ASSET_DEFINITION_ID)
        check(TairaTransferCodec.canonicalQuantity(request.amount) == request.amount)
        check(IrohaAddressCodec.parse(request.account.address, request.network.chainDiscriminant).publicKeyHex == request.account.publicKey)
    }

    private suspend fun derive(request: NexusTransferSigningRequest): IrohaKeyDerivation.DerivedAccount {
        val wallet = users.getSoraAccount(request.account.walletId)
        check(wallet.substrateAddress == request.account.walletId && !credentials.isExplicitWatchOnly(wallet)) { "TAIRA_WALLET_NOT_SIGNABLE" }
        val derived = IrohaKeyDerivation.derive(credentials.retrieveMnemonic(wallet), request.network)
        if (derived.address != request.account.address || TairaTransferCodec.hex(derived.publicKey) != request.account.publicKey) {
            derived.destroy(); error("TAIRA_DERIVED_ACCOUNT_MISMATCH")
        }
        return derived
    }
}

/** Taira deliberately uses node-reported HTTPS applied checkpoints; production remains unavailable. */
class TairaHttpsConfirmationReader(private val client: TairaSigningClient = TairaSigningClient()) : NexusFinalityReader {
    override fun isQualifiedFor(network: NexusNetwork): Boolean = network == NexusNetworks.taira
    override suspend fun finalizedCheckpoint(network: NexusNetwork): NexusFinalityCheckpoint {
        check(isQualifiedFor(network)) { "TAIRA_CONFIRMATION_WRONG_NETWORK" }
        val checkpoint = client.checkpoint()
        return NexusFinalityCheckpoint(network.id, network.chainId, checkpoint.height, checkpoint.hash)
    }
}
