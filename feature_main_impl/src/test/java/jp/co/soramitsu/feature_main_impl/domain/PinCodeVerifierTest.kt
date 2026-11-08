package jp.co.soramitsu.feature_main_impl.domain

import io.mockk.*
import jp.co.soramitsu.feature_account_api.domain.WalletPinVerifier
import jp.co.soramitsu.feature_account_api.domain.WalletPinLockedException
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PinCodeVerifierTest {
    private var tries = 0
    private var started = 0L
    private val repository = mockk<UserRepository> {
        coEvery { retrievePin() } returns "123456"
        coEvery { retrievePinTriesUsed() } answers { tries }
        coEvery { retrieveTimerStartedTimestamp() } answers { started }
        coEvery { savePinTriesUsed(any()) } coAnswers { tries = firstArg() }
        coEvery { saveTimerStartedTimestamp(any()) } coAnswers { started = firstArg() }
        coEvery { resetTriesUsed() } coAnswers { tries = 0 }
        coEvery { resetTimerStartedTimestamp() } coAnswers { started = 0L }
    }

    @Test fun `parallel flows share the persistent budget and never compare PINs during lockout`() = runTest {
        val first = WalletPinVerifier(repository)
        val second = WalletPinVerifier(repository)
        (0..9).map { i -> async {
            runCatching { (if (i % 2 == 0) first else second).verify("654321") }
        } }.awaitAll()
        assertEquals(3, tries)
        coVerify(exactly = 3) { repository.retrievePin() }
        assertTrue(runCatching { WalletPinVerifier(repository).verify("123456") }
            .exceptionOrNull() is WalletPinLockedException)
        coVerify(exactly = 3) { repository.retrievePin() }
    }

    @Test fun `correct PIN resets budget after cooldown and next failure starts at one`() = runTest {
        tries = 3
        started = System.currentTimeMillis() - 61_000
        val verifier = WalletPinVerifier(repository)
        assertTrue(verifier.verify("123456"))
        assertEquals(0, tries)
        assertEquals(0L, started)
        assertFalse(verifier.verify("000000"))
        assertEquals(1, tries)
    }

    @Test fun `empty or unreadable stored PIN never authorizes`() = runTest {
        val verifier = WalletPinVerifier(repository)
        coEvery { repository.retrievePin() } returns ""
        assertFalse(verifier.verify(""))
        coEvery { repository.retrievePin() } throws IllegalStateException("fixture unavailable")
        assertTrue(runCatching { verifier.verify("123456") }.isFailure)
        coVerify(exactly = 0) { repository.resetTriesUsed() }
    }
}
