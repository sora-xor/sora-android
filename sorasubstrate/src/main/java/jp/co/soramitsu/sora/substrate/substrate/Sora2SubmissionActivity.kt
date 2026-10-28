package jp.co.soramitsu.sora.substrate.substrate

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.TransactionNodeRefusal

/**
 * What one locally signed SORA2 transaction does, in the terms the user reviewed: its kind, the
 * tokens and the amounts. It holds no key material and no signed bytes.
 *
 * The durable status journal ([jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal])
 * deliberately knows only the transaction hash. This description is kept next to it so that a
 * transaction which is still waiting for confirmation is shown in Activity as what it is ("Swap
 * 10 XOR for VAL"), also in a token's own activity and after the app was restarted.
 *
 * Field use by [kind]:
 * - [Kind.TRANSFER]: one token, one amount, [peer] is the recipient.
 * - [Kind.SWAP]: tokens and amounts are (from, to); [market] is the liquidity source.
 * - [Kind.LIQUIDITY_ADD], [Kind.LIQUIDITY_REMOVE]: the two pool tokens and their amounts.
 * - [Kind.REFERRAL_BOND], [Kind.REFERRAL_UNBOND]: XOR and the bonded amount.
 * - [Kind.REFERRAL_SET_REFERRER]: XOR; [peer] is the referrer.
 * - [Kind.DEMETER_STAKE], [Kind.DEMETER_UNSTAKE], [Kind.DEMETER_CLAIM]: tokens are (base, target,
 *   reward) and there is one amount.
 * - [Kind.LEGACY_CLAIM]: no tokens.
 */
data class Sora2SubmissionActivity(
    val kind: Kind,
    val tokenIds: List<String> = emptyList(),
    val amounts: List<String> = emptyList(),
    val fee: String? = null,
    val peer: String? = null,
    val market: String? = null,
    /**
     * True only when the user was told that the same action is still waiting for confirmation
     * and deliberately chose to send it again. Never stored.
     */
    val duplicateConfirmed: Boolean = false,
) {
    enum class Kind {
        TRANSFER,
        SWAP,
        LIQUIDITY_ADD,
        LIQUIDITY_REMOVE,
        REFERRAL_BOND,
        REFERRAL_UNBOND,
        REFERRAL_SET_REFERRER,
        DEMETER_STAKE,
        DEMETER_UNSTAKE,
        DEMETER_CLAIM,
        LEGACY_CLAIM,
    }

    /** Same kind of action on the same tokens (and the same counterparty); amounts may differ. */
    fun isSameActionAs(other: Sora2SubmissionActivity): Boolean =
        kind == other.kind && tokenIds == other.tokenIds && peer == other.peer

    internal fun isWellFormed(): Boolean =
        tokenIds.size <= MAXIMUM_TOKENS &&
            amounts.size <= MAXIMUM_TOKENS &&
            tokenIds.all { it.isBoundedText() } &&
            amounts.all { it.isPlainAmount() } &&
            (fee == null || fee.isPlainAmount()) &&
            (peer == null || peer.isBoundedText()) &&
            (market == null || market.isBoundedText())

    companion object {
        internal const val MAXIMUM_TOKENS = 4
        internal const val MAXIMUM_TEXT_CHARS = 128
        private val PLAIN_AMOUNT = Regex("^[0-9]{1,60}(\\.[0-9]{1,40})?$")

        private fun String.isBoundedText(): Boolean =
            isNotEmpty() && length <= MAXIMUM_TEXT_CHARS && none { it.isISOControl() }

        private fun String.isPlainAmount(): Boolean = PLAIN_AMOUNT.matches(this)

        private fun BigDecimal.plain(): String = max(BigDecimal.ZERO).toPlainString()

        fun transfer(tokenId: String, amount: BigDecimal, recipient: String, fee: BigDecimal?) =
            Sora2SubmissionActivity(
                kind = Kind.TRANSFER,
                tokenIds = listOf(tokenId),
                amounts = listOf(amount.plain()),
                fee = fee?.plain(),
                peer = recipient,
            )

        fun swap(
            tokenFromId: String,
            tokenToId: String,
            amountFrom: BigDecimal,
            amountTo: BigDecimal,
            fee: BigDecimal?,
            market: String?,
        ) = Sora2SubmissionActivity(
            kind = Kind.SWAP,
            tokenIds = listOf(tokenFromId, tokenToId),
            amounts = listOf(amountFrom.plain(), amountTo.plain()),
            fee = fee?.plain(),
            market = market,
        )

        fun liquidity(
            add: Boolean,
            token1Id: String,
            token2Id: String,
            amount1: BigDecimal,
            amount2: BigDecimal,
            fee: BigDecimal?,
        ) = Sora2SubmissionActivity(
            kind = if (add) Kind.LIQUIDITY_ADD else Kind.LIQUIDITY_REMOVE,
            tokenIds = listOf(token1Id, token2Id),
            amounts = listOf(amount1.plain(), amount2.plain()),
            fee = fee?.plain(),
        )

        fun referralBond(bond: Boolean, tokenId: String, amount: BigDecimal, fee: BigDecimal?) =
            Sora2SubmissionActivity(
                kind = if (bond) Kind.REFERRAL_BOND else Kind.REFERRAL_UNBOND,
                tokenIds = listOf(tokenId),
                amounts = listOf(amount.plain()),
                fee = fee?.plain(),
            )

        fun referralSetReferrer(tokenId: String, referrer: String) =
            Sora2SubmissionActivity(
                kind = Kind.REFERRAL_SET_REFERRER,
                tokenIds = listOf(tokenId),
                peer = referrer,
            )

        fun demeter(
            kind: Kind,
            baseTokenId: String,
            targetTokenId: String,
            rewardTokenId: String,
            amount: BigDecimal,
            fee: BigDecimal?,
        ): Sora2SubmissionActivity {
            require(
                kind == Kind.DEMETER_STAKE ||
                    kind == Kind.DEMETER_UNSTAKE ||
                    kind == Kind.DEMETER_CLAIM
            ) { "SORA2_ACTIVITY_KIND_INVALID" }
            return Sora2SubmissionActivity(
                kind = kind,
                tokenIds = listOf(baseTokenId, targetTokenId, rewardTokenId),
                amounts = listOf(amount.plain()),
                fee = fee?.plain(),
            )
        }

        fun legacyClaim() = Sora2SubmissionActivity(kind = Kind.LEGACY_CLAIM)
    }
}

/** The node's explicit answer to the single submission attempt, kept for Activity. */
data class Sora2NodeRefusal(
    val reason: TransactionNodeRefusal,
    val code: Long,
    /** Short, sanitized text from the node. Untrusted; shown as plain text only. */
    val message: String?,
)

/** Everything known locally about one journaled SORA2 transaction besides its status. */
data class Sora2SubmissionDetails(
    val walletId: String,
    val transactionHash: String,
    val createdAt: Long,
    val activity: Sora2SubmissionActivity? = null,
    val nodeRefusal: Sora2NodeRefusal? = null,
    /** Runtime error of a transaction that failed on chain, when it could be decoded. */
    val chainFailure: String? = null,
)
