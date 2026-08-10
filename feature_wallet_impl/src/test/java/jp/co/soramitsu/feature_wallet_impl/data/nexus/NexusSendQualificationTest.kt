package jp.co.soramitsu.feature_wallet_impl.data.nexus

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.nexus.NexusNetworks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NexusSendQualificationTest {

    @Test
    fun `send qualification requires both capabilities without exercising either capability`() {
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

            val qualification = DefaultNexusSendQualification(signer, finalityReader)

            assertEquals(expected, qualification.isQualifiedFor(NexusNetworks.minamoto))
            coVerify(exactly = 0) { signer.quote(any()) }
            coVerify(exactly = 0) { signer.sign(any(), any()) }
            coVerify(exactly = 0) { finalityReader.finalizedCheckpoint(any()) }
        }
    }

    @Test
    fun `qualification adapter failures disable sends without invoking sensitive operations`() {
        val signerFailure = mockk<NexusTransactionSigner>()
        val untouchedFinalityReader = mockk<NexusFinalityReader>()
        every {
            signerFailure.isQualifiedFor(NexusNetworks.taira)
        } throws IllegalStateException("signer adapter unavailable")
        val signerFailureQualification = DefaultNexusSendQualification(
            signerFailure,
            untouchedFinalityReader,
        )

        assertFalse(signerFailureQualification.isQualifiedFor(NexusNetworks.taira))
        verify(exactly = 0) { untouchedFinalityReader.isQualifiedFor(any()) }

        val qualifiedSigner = mockk<NexusTransactionSigner>()
        val finalityFailure = mockk<NexusFinalityReader>()
        every { qualifiedSigner.isQualifiedFor(NexusNetworks.taira) } returns true
        every {
            finalityFailure.isQualifiedFor(NexusNetworks.taira)
        } throws IllegalStateException("finality adapter unavailable")
        val finalityFailureQualification = DefaultNexusSendQualification(
            qualifiedSigner,
            finalityFailure,
        )

        assertFalse(finalityFailureQualification.isQualifiedFor(NexusNetworks.taira))
        coVerify(exactly = 0) { signerFailure.quote(any()) }
        coVerify(exactly = 0) { signerFailure.sign(any(), any()) }
        coVerify(exactly = 0) { qualifiedSigner.quote(any()) }
        coVerify(exactly = 0) { qualifiedSigner.sign(any(), any()) }
        coVerify(exactly = 0) { untouchedFinalityReader.finalizedCheckpoint(any()) }
        coVerify(exactly = 0) { finalityFailure.finalizedCheckpoint(any()) }
    }

    @Test
    fun `native linkage failures disable sends without invoking sensitive operations`() {
        val signerFailure = mockk<NexusTransactionSigner>()
        val untouchedFinalityReader = mockk<NexusFinalityReader>()
        every {
            signerFailure.isQualifiedFor(NexusNetworks.taira)
        } throws UnsatisfiedLinkError("signer native artifact unavailable")
        val signerFailureQualification = DefaultNexusSendQualification(
            signerFailure,
            untouchedFinalityReader,
        )

        assertFalse(signerFailureQualification.isQualifiedFor(NexusNetworks.taira))
        verify(exactly = 0) { untouchedFinalityReader.isQualifiedFor(any()) }

        val qualifiedSigner = mockk<NexusTransactionSigner>()
        val finalityFailure = mockk<NexusFinalityReader>()
        every { qualifiedSigner.isQualifiedFor(NexusNetworks.taira) } returns true
        every {
            finalityFailure.isQualifiedFor(NexusNetworks.taira)
        } throws UnsatisfiedLinkError("finality native artifact unavailable")
        val finalityFailureQualification = DefaultNexusSendQualification(
            qualifiedSigner,
            finalityFailure,
        )

        assertFalse(finalityFailureQualification.isQualifiedFor(NexusNetworks.taira))
        coVerify(exactly = 0) { signerFailure.quote(any()) }
        coVerify(exactly = 0) { signerFailure.sign(any(), any()) }
        coVerify(exactly = 0) { qualifiedSigner.quote(any()) }
        coVerify(exactly = 0) { qualifiedSigner.sign(any(), any()) }
        coVerify(exactly = 0) { untouchedFinalityReader.finalizedCheckpoint(any()) }
        coVerify(exactly = 0) { finalityFailure.finalizedCheckpoint(any()) }
    }
}
