package jp.co.soramitsu.feature_wallet_impl.domain

import io.mockk.coEvery
import io.mockk.mockk
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CardsHubInteractorSigningCountsTest {

    private val userRepository = mockk<UserRepository>()
    private val interactor = CardsHubInteractorImpl(userRepository, mockk())

    @Test
    fun `read-only accounts are not counted as able to sign`() = runTest {
        coEvery { userRepository.getSoraAccountsCount() } returns 3
        coEvery { userRepository.unavailableWalletIds() } returns setOf("cnReadOnly")
        assertEquals(3 to 2, interactor.accountSigningCounts())

        coEvery { userRepository.unavailableWalletIds() } returns emptySet()
        assertEquals(3 to 3, interactor.accountSigningCounts())

        // Even the only account can be read-only: nothing is reported as verified then.
        coEvery { userRepository.getSoraAccountsCount() } returns 1
        coEvery { userRepository.unavailableWalletIds() } returns setOf("cnOnly")
        assertEquals(1 to 0, interactor.accountSigningCounts())
    }
}
