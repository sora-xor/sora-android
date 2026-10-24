package jp.co.soramitsu.common.account

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletMutationCoordinatorTest {

    @Test
    fun `same coroutine may enter signing while wallet mutation is locked`() = runTest {
        var nestedOperationRan = false

        WalletMutationCoordinator.withLock {
            WalletMutationCoordinator.withLock {
                nestedOperationRan = true
            }
        }

        assertTrue(nestedOperationRan)
    }
}
