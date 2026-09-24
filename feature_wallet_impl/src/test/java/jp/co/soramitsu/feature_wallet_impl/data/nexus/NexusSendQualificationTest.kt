package jp.co.soramitsu.feature_wallet_impl.data.nexus

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.nexus.NexusNetworks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusSendQualificationTest {

    @Test
    fun `send support explicitly projects signer and finality capabilities`() {
        listOf(
            Triple(false, false, false),
            Triple(false, true, false),
            Triple(true, false, false),
            Triple(true, true, true),
        ).forEach { (signerQualified, finalityQualified, expected) ->
            val signer = mockk<NexusTransactionSigner>()
            val finalityReader = mockk<NexusFinalityReader>()
            every { signer.isQualifiedFor(NexusNetworks.minamoto) } returns signerQualified
            every {
                finalityReader.isQualifiedFor(NexusNetworks.minamoto)
            } returns finalityQualified

            val capabilities = DefaultNexusSendQualification(signer, finalityReader)
                .capabilitiesFor(NexusNetworks.minamoto)

            assertEquals(
                if (signerQualified) NexusCapabilityStatus.QUALIFIED
                else NexusCapabilityStatus.UNAVAILABLE,
                capabilities.signing,
            )
            assertEquals(
                if (finalityQualified) NexusCapabilityStatus.QUALIFIED
                else NexusCapabilityStatus.UNAVAILABLE,
                capabilities.finality,
            )
            assertEquals(expected, capabilities.supportsSend)
            coVerify(exactly = 0) { signer.quote(any()) }
            coVerify(exactly = 0) { signer.sign(any(), any()) }
            coVerify(exactly = 0) { finalityReader.finalizedCheckpoint(any()) }
        }
    }

    @Test
    fun `adapter and native linkage failures remain visible as capability errors`() {
        listOf<Throwable>(
            IllegalStateException("adapter unavailable"),
            UnsatisfiedLinkError("native artifact unavailable"),
        ).forEach { failure ->
            val signer = mockk<NexusTransactionSigner>()
            val finalityReader = mockk<NexusFinalityReader>()
            every { signer.isQualifiedFor(TEST_TAIRA_NETWORK) } throws failure
            every { finalityReader.isQualifiedFor(TEST_TAIRA_NETWORK) } returns false

            val capabilities = DefaultNexusSendQualification(signer, finalityReader)
                .capabilitiesFor(TEST_TAIRA_NETWORK)

            assertEquals(NexusCapabilityStatus.ERROR, capabilities.signing)
            assertEquals(NexusCapabilityStatus.UNAVAILABLE, capabilities.finality)
            assertFalse(capabilities.supportsSend)
            coVerify(exactly = 0) { signer.quote(any()) }
            coVerify(exactly = 0) { signer.sign(any(), any()) }
            coVerify(exactly = 0) { finalityReader.finalizedCheckpoint(any()) }
        }
    }

    @Test
    fun `unavailable production adapters never advertise send support`() {
        val capabilities = DefaultNexusSendQualification(
            UnavailableNexusTransactionSigner(),
            UnavailableNexusFinalityReader(),
        ).capabilitiesFor(TEST_TAIRA_NETWORK)

        assertEquals(NexusCapabilityStatus.UNAVAILABLE, capabilities.signing)
        assertEquals(NexusCapabilityStatus.UNAVAILABLE, capabilities.finality)
        assertFalse(capabilities.supportsSend)
        assertTrue(NexusSendCapabilities.unavailable() == capabilities)
    }
}
