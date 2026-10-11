package jp.co.soramitsu.feature_assets_impl.data

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.account.WalletMutationCoordinator
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.domain.WhitelistTokensManager
import jp.co.soramitsu.common_wallet.data.AssetLocalToAssetMapper
import jp.co.soramitsu.common_wallet.data.XorBalanceDto
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The last balance check of a transfer runs under the wallet lock and reads over the selected
 * node's WebSocket, which keeps a request queued without limit while it is disconnected
 * (verdicts.md TX-HOST-FROZEN, new finding). The real repository is used; the signing manager
 * hands the test the balance check exactly as it would run it at the pre-transport boundary.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferFreshBalanceReadTest {

    // The repository starts a token refresh on the application scope; it is never advanced.
    private val applicationScope =
        CoroutineScope(SupervisorJob() + StandardTestDispatcher(TestCoroutineScheduler()))
    private val substrateCalls = mockk<SubstrateCalls>()
    private val extrinsicManager = mockk<ExtrinsicManager>()
    private val balanceCheck = slot<suspend (BigInteger) -> Unit>()

    @After
    fun tearDown() {
        applicationScope.cancel()
    }

    private fun TestScope.repository(): AssetsRepositoryImpl {
        val coroutineManager = mockk<CoroutineManager>().also {
            every { it.applicationScope } returns applicationScope
            every { it.io } returns StandardTestDispatcher(testScheduler)
        }
        val config = mockk<SoraConfigManager>(relaxed = true)
        val whitelist = mockk<WhitelistTokensManager>(relaxed = true)
        coEvery {
            extrinsicManager.submitFeeQualifiedAndWatchExtrinsic(
                from = any(),
                keypair = any(),
                reviewedFee = any(),
                useBatchAll = any(),
                activity = any(),
                preSigningValidation = any(),
                validateBalances = capture(balanceCheck),
                formExtrinsic = any(),
            )
        } returns ExtrinsicSubmitStatus(true, "0x01", "0x02")
        return AssetsRepositoryImpl(
            db = mockk<AppDatabase>(relaxed = true),
            assetLocalToAssetMapper = AssetLocalToAssetMapper(whitelist, config),
            extrinsicManager = extrinsicManager,
            substrateCalls = substrateCalls,
            soraConfigManager = config,
            coroutineManager = coroutineManager,
            whitelistTokensManager = whitelist,
        )
    }

    /** Starts a 1 XOR transfer and returns the balance check the signing manager was given. */
    private suspend fun TestScope.balanceCheckOfTransfer(): suspend (BigInteger) -> Unit {
        repository().observeTransfer(
            keypair = Sr25519Keypair(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3)),
            from = "cnFrom",
            to = "cnTo",
            token = TestTokens.xorToken,
            amount = BigDecimal.ONE,
            fee = BigDecimal("0.0007"),
            validateSelectedWallet = {},
            confirmedDuplicate = false,
        )
        return balanceCheck.captured
    }

    @Test
    fun `a balance read that never answers ends the transfer as not sent and frees the lock`() =
        runTest {
            coEvery { substrateCalls.fetchXORBalances("cnFrom") } coAnswers { awaitCancellation() }
            val check = balanceCheckOfTransfer()
            var failure: Throwable? = null
            var otherWorkRan = false

            // As in ExtrinsicManager: the check runs while the wallet lock is held.
            launch {
                failure = runCatching {
                    WalletMutationCoordinator.withLock { check(BigInteger.TEN.pow(15)) }
                }.exceptionOrNull()
            }
            runCurrent()
            // Switching nodes needs the same lock and has to wait for now.
            launch { WalletMutationCoordinator.withLock { otherWorkRan = true } }
            advanceTimeBy(FRESH_BALANCE_READ_TIMEOUT_MILLIS - 1)
            runCurrent()
            assertFalse(otherWorkRan)
            assertEquals(null, failure)

            advanceTimeBy(2)
            runCurrent()

            assertTrue(failure is IOException)
            assertEquals("SORA2_TRANSFER_BALANCE_READ_TIMED_OUT", failure?.message)
            // Told as "couldn't reach the network, nothing was sent", with Try again.
            assertEquals(
                NetworkFeeUnavailable.NETWORK_UNREACHABLE,
                NetworkFeeUnavailable.from(IllegalStateException("pre-transport", failure)),
            )
            assertTrue(otherWorkRan)
        }

    @Test
    fun `an answer in time is checked against the locked balance`() = runTest {
        val xor = BigInteger.TEN.pow(18)
        // 3 XOR free, 2.5 XOR of it locked: 1 XOR plus the fee cannot be sent.
        coEvery { substrateCalls.fetchXORBalances("cnFrom") } returns XorBalanceDto(
            free = xor * BigInteger.valueOf(3),
            reserved = BigInteger.ZERO,
            miscFrozen = xor * BigInteger.valueOf(5) / BigInteger.TWO,
            feeFrozen = xor * BigInteger.valueOf(5) / BigInteger.TWO,
            bonded = BigInteger.ZERO,
            redeemable = BigInteger.ZERO,
            unbonding = BigInteger.ZERO,
        )
        val check = balanceCheckOfTransfer()

        val failure = runCatching { check(BigInteger.TEN.pow(15)) }.exceptionOrNull()

        assertEquals("SORA2_TRANSFER_XOR_BALANCE_INSUFFICIENT", failure?.message)

        // With 1.5 XOR locked, 1.5 XOR is free to move and the transfer goes ahead.
        coEvery { substrateCalls.fetchXORBalances("cnFrom") } returns XorBalanceDto(
            free = xor * BigInteger.valueOf(3),
            reserved = BigInteger.ZERO,
            miscFrozen = xor * BigInteger.valueOf(3) / BigInteger.TWO,
            feeFrozen = xor * BigInteger.valueOf(3) / BigInteger.TWO,
            bonded = BigInteger.ZERO,
            redeemable = BigInteger.ZERO,
            unbonding = BigInteger.ZERO,
        )
        check(BigInteger.TEN.pow(15))
    }
}
