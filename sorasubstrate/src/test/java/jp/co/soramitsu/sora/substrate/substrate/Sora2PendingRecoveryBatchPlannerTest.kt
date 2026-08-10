package jp.co.soramitsu.sora.substrate.substrate

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class Sora2PendingRecoveryBatchPlannerTest {

    @Test
    fun `failing first batch rotates to every later witness`() {
        val ordered = (0 until 10).toList()

        val first = Sora2PendingRecoveryBatchPlanner.select(ordered, cursor = 0, maximum = 3)
        val second = Sora2PendingRecoveryBatchPlanner.select(
            ordered,
            cursor = first.nextCursor,
            maximum = 3,
        )
        val third = Sora2PendingRecoveryBatchPlanner.select(
            ordered,
            cursor = second.nextCursor,
            maximum = 3,
        )
        val fourth = Sora2PendingRecoveryBatchPlanner.select(
            ordered,
            cursor = third.nextCursor,
            maximum = 3,
        )

        assertEquals(ordered, (first.rows + second.rows + third.rows + fourth.rows).distinct())
    }

    @Test
    fun `foreground priority does not disable bounded rotation`() {
        val ordered = (0 until 6).toList()
        val first = Sora2PendingRecoveryBatchPlanner.select(
            ordered = ordered,
            cursor = 0,
            maximum = 3,
            priority = 5,
        )
        val second = Sora2PendingRecoveryBatchPlanner.select(
            ordered = ordered,
            cursor = first.nextCursor,
            maximum = 3,
            priority = 5,
        )
        val third = Sora2PendingRecoveryBatchPlanner.select(
            ordered = ordered,
            cursor = second.nextCursor,
            maximum = 3,
            priority = 5,
        )

        assertEquals(listOf(5, 0, 1), first.rows)
        assertEquals(listOf(5, 2, 3), second.rows)
        assertEquals(ordered, (first.rows + second.rows + third.rows).distinct().sorted())
    }

    @Test
    fun `priority only foreground poll leaves background cursor intact`() {
        val selected = Sora2PendingRecoveryBatchPlanner.select(
            ordered = (0 until 6).toList(),
            cursor = 4,
            maximum = 1,
            priority = 2,
        )

        assertEquals(listOf(2), selected.rows)
        assertEquals(4, selected.nextCursor)
    }

    @Test
    fun `unresolved generic witness blocks a second legacy migration for the same wallet`() {
        val witness = mockk<Sora2PendingSubmissionLocal>()
        every { witness.walletId } returns "wallet-1"
        every { witness.operationKind } returns Sora2PendingSubmissionLocal.OPERATION_GENERIC

        val error = try {
            Sora2LegacyMigrationAdmissionGate.requireNoUnresolvedWitness(
                walletId = "wallet-1",
                unresolved = listOf(witness),
            )
            fail("Expected an unresolved legacy migration witness to block another claim")
            null
        } catch (error: Throwable) {
            error
        }

        assertEquals("SORA2_LEGACY_MIGRATION_ALREADY_UNRESOLVED", error?.message)
    }

    @Test
    fun `legacy migration gate ignores other wallets and Polkamarkt witnesses`() {
        val otherWallet = mockk<Sora2PendingSubmissionLocal>()
        every { otherWallet.walletId } returns "wallet-2"
        every { otherWallet.operationKind } returns Sora2PendingSubmissionLocal.OPERATION_GENERIC
        val polkamarkt = mockk<Sora2PendingSubmissionLocal>()
        every { polkamarkt.walletId } returns "wallet-1"
        every { polkamarkt.operationKind } returns
            Sora2PendingSubmissionLocal.OPERATION_POLKAMARKT

        Sora2LegacyMigrationAdmissionGate.requireNoUnresolvedWitness(
            walletId = "wallet-1",
            unresolved = listOf(otherWallet, polkamarkt),
        )
    }
}
