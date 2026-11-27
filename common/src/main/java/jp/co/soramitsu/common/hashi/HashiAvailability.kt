package jp.co.soramitsu.common.hashi

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.format.DateTimeParseException
import jp.co.soramitsu.common.ethereum.EthHex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** The `:sora-nexus:*` well-known storage keys (raw UTF-8 keys, not pallet storage). */
object HashiRecoveryKeys {

    const val POLICY = ":sora-nexus:recovery:policy:v1"
    const val STATUS = ":sora-nexus:recovery:status:v1"
    const val ACTIVE_POLICY = ":sora-nexus:recovery:active-policy:v1"
    const val ANCHOR = ":sora-nexus:recovery:branch-anchor:v1"
    const val TERMINAL = ":sora-nexus:recovery:terminal-audit:v1"
    const val PENDING = ":sora-nexus:recovery:incoming-audit-pending:v1"

    /** Present only while recovery work runs; present with an empty value still counts. */
    val TRANSIENT = listOf(
        ":sora-nexus:recovery:bridge-inbound-context:v1",
        ":sora-nexus:recovery:incoming-active:v1",
        ":sora-nexus:recovery:incoming-failed:v1",
        ":sora-nexus:recovery:hashi-resolution:v1",
    )

    /** Read by consensus only before runtime 142. */
    const val LEGACY_BRIDGE_HALT = ":sora-nexus:recovery:bridge-halt:v1"

    const val GUARDIAN_HALT = ":sora-nexus:bridge-halt:latest:v1"

    val RECOVERY: List<String> = listOf(POLICY, STATUS, ACTIVE_POLICY, ANCHOR, TERMINAL, PENDING) + TRANSIENT

    fun hex(key: String): String = EthHex.encode(key.toByteArray(Charsets.US_ASCII))
}

/**
 * Port of `completion_with` (sora2-network 4.8.15,
 * `vendor/sora2-common/pallets/types/src/recovery/lifecycle.rs`), the rule runtime 142 uses to
 * admit ordinary bridge calls. [read] returns the raw value of a key or null when it is absent; a
 * reader that fails must throw, never return null.
 */
object HashiRecoveryCompletion {

    enum class Verdict {
        /** No recovery keys at all: admitted. */
        NONE,

        /** A consistent, completed recovery: admitted. */
        COMPLETE,

        /** Unfinished, malformed or inconsistent recovery: ordinary bridge calls are refused. */
        INCOMPLETE;

        val admitted: Boolean get() = this != INCOMPLETE
    }

    private class Status(val policy: ByteArray, val phase: Int, val chunks: Long)

    fun evaluate(read: (String) -> ByteArray?): Verdict {
        val policyBytes = read(HashiRecoveryKeys.POLICY)
        val statusBytes = read(HashiRecoveryKeys.STATUS)
        if (policyBytes == null) {
            val anyOther = statusBytes != null ||
                listOf(HashiRecoveryKeys.ACTIVE_POLICY, HashiRecoveryKeys.ANCHOR, HashiRecoveryKeys.TERMINAL, HashiRecoveryKeys.PENDING)
                    .any { read(it) != null } ||
                HashiRecoveryKeys.TRANSIENT.any { read(it) != null }
            return if (anyOther) Verdict.INCOMPLETE else Verdict.NONE
        }
        val policy = hash(policyBytes) ?: return Verdict.INCOMPLETE
        val status = status(statusBytes) ?: return Verdict.INCOMPLETE
        if (status.phase != 4 || !status.policy.contentEquals(policy)) return Verdict.INCOMPLETE
        val activePolicy = hash(read(HashiRecoveryKeys.ACTIVE_POLICY)) ?: return Verdict.INCOMPLETE
        hash(read(HashiRecoveryKeys.ANCHOR)) ?: return Verdict.INCOMPLETE
        val pending = read(HashiRecoveryKeys.PENDING)
        if (pending == null || !pending.contentEquals(ByteArray(4)) || HashiRecoveryKeys.TRANSIENT.any { read(it) != null }) {
            return Verdict.INCOMPLETE
        }
        val terminal = read(HashiRecoveryKeys.TERMINAL) ?: return Verdict.INCOMPLETE
        if (terminal.size != 68) return Verdict.INCOMPLETE
        val active = terminal.copyOfRange(0, 32)
        val audit = terminal.copyOfRange(32, 64)
        val chunks = ByteBuffer.wrap(terminal, 64, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        if (!active.contentEquals(activePolicy) || audit.all { it == 0.toByte() } || chunks != status.chunks) {
            return Verdict.INCOMPLETE
        }
        return Verdict.COMPLETE
    }

    /** `(u32 version, [u8;32] policy, u8 phase, u32 chunks, u64 operation, u8 failure_tag)`, 50 bytes. */
    private fun status(bytes: ByteArray?): Status? {
        if (bytes == null || bytes.size != 50) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = buffer.int
        val policy = ByteArray(32).also { buffer.get(it) }
        val phase = buffer.get().toInt() and 0xff
        val chunks = buffer.int.toLong() and 0xffffffffL
        buffer.long
        val failureTag = buffer.get().toInt()
        if (version != 1 || policy.all { it == 0.toByte() } || failureTag != 0) return null
        return Status(policy, phase, chunks)
    }

    private fun hash(bytes: ByteArray?): ByteArray? =
        bytes?.takeIf { it.size == 32 && it.any { byte -> byte != 0.toByte() } }
}

/** The operators' signer report (`hashi_bridge_status` in the app's remote config). */
class HashiSignerReport(
    val reportedAt: Instant,
    val activeSigners: Int,
    val registeredPeers: Int,
    val requiredSignatures: Int,
)

object HashiSignerReports {

    const val CONFIG_URL = "https://config.polkaswap2.io/prod/mobile.json"
    const val CONFIG_KEY = "hashi_bridge_status"
    const val MAXIMUM_AGE_SECONDS = 3_600L

    enum class Verdict {
        /** Fresh, consistent with the chain's peer set, and enough signers are active. */
        UP,
        MISSING,
        STALE,
        MISMATCH,
        DOWN;

        val up: Boolean get() = this == UP
    }

    /** The report in [configJson], or null when the key is missing or not the documented shape. */
    fun parse(configJson: String): HashiSignerReport? {
        val root = runCatching { Json.parseToJsonElement(configJson) }.getOrNull() as? JsonObject ?: return null
        val report = root[CONFIG_KEY] as? JsonObject ?: return null
        val reportedAt = (report["reportedAt"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        fun count(name: String) = (report[name] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull?.takeIf { it >= 0 }
        val instant = try {
            Instant.parse(reportedAt)
        } catch (_: DateTimeParseException) {
            return null
        }
        return HashiSignerReport(
            reportedAt = instant,
            activeSigners = count("activeSigners") ?: return null,
            registeredPeers = count("registeredPeers") ?: return null,
            requiredSignatures = count("requiredSignatures") ?: return null,
        )
    }

    /** `peers − ⌊(peers − 1)/3⌋ (+1 while a peer is being added or removed)`. */
    fun requiredSignatures(peers: Int, pendingPeer: Boolean): Int {
        require(peers > 0) { "HASHI_PEERS_INVALID" }
        return peers - (peers - 1) / 3 + if (pendingPeer) 1 else 0
    }

    fun judge(report: HashiSignerReport?, now: Instant, peers: Int, pendingPeer: Boolean): Verdict {
        if (report == null) return Verdict.MISSING
        val age = now.epochSecond - report.reportedAt.epochSecond
        if (age < 0 || age > MAXIMUM_AGE_SECONDS) return Verdict.STALE
        if (peers <= 0 || report.registeredPeers != peers ||
            report.requiredSignatures != requiredSignatures(peers, pendingPeer)
        ) {
            return Verdict.MISMATCH
        }
        return if (report.activeSigners >= report.requiredSignatures) Verdict.UP else Verdict.DOWN
    }
}

/** One reason new transfers are off. */
enum class HashiBlock {
    /** Consensus would refuse bridge calls (recovery unfinished, or the pre-142 latch is set). */
    RECOVERY_NOT_COMPLETE,

    /** Guardians stopped the bridge workers. */
    GUARDIAN_HALT,

    /** `bridgeStatuses(0)` is not `Initialized`. */
    NOT_INITIALIZED,

    /** A queued request is older than [HashiAvailability.STALL_BLOCKS]: signers are not processing. */
    STALLED_REQUESTS,

    /** No fresh, consistent operator signer report. */
    SIGNERS_UNVERIFIED,

    /** The operators report fewer active signers than required. */
    SIGNERS_DOWN,

    /** A contract address on SORA is not the reviewed one. */
    CONTRACT_MISMATCH,

    /** A read failed: availability cannot be proven. */
    READ_FAILED,
}

/**
 * Whether NEW transfers (either direction) may start. Claims of withdrawals SORA already approved
 * need no signers and stay available whatever this says.
 */
class HashiAvailability(
    val blocks: Set<HashiBlock>,
    val specVersion: Int?,
    val finalizedBlock: Long?,
    val signerVerdict: HashiSignerReports.Verdict?,
) {
    val newTransfersAllowed: Boolean get() = blocks.isEmpty()

    companion object {
        /** About 30 minutes of 6-second blocks. */
        const val STALL_BLOCKS = 300L

        /** The newest runtime whose admission rule was reviewed. */
        const val REVIEWED_SPEC_VERSION = 142

        fun readFailed(): HashiAvailability = HashiAvailability(setOf(HashiBlock.READ_FAILED), null, null, null)
    }
}
