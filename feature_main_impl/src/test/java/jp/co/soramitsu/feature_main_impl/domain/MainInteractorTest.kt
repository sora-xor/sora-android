package jp.co.soramitsu.feature_main_impl.domain

import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class MainInteractorTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    private lateinit var interactor: MainInteractor

    @Before
    fun setUp() {
        interactor = MainInteractor(
            userRepository,
            credentialsRepository,
        )
    }

    @Test
    fun `getMnemonic() function returns not empty mnemonic`() = runBlockingTest {
        val mnemonic = "test mnemonic"
        given(credentialsRepository.retrieveMnemonic()).willReturn(mnemonic)
        val mnemonicActual = interactor.getMnemonic()
        verify(credentialsRepository).retrieveMnemonic()
        assertEquals(mnemonic, mnemonicActual)
    }

    @Test
    fun `getMnemonic() function returns empty mnemonic`() = runBlockingTest {
        val mnemonic = ""
        given(credentialsRepository.retrieveMnemonic()).willReturn(mnemonic)
        val result = runCatching {
            interactor.getMnemonic()
        }
        assertTrue(result.isFailure)
        verify(credentialsRepository).retrieveMnemonic()
    }

    @Test
    fun `getInviteCode() calls userRepository getParentInviteCode()`() = runBlockingTest {
        val expectedResult = "parentInviteCode"
        given(userRepository.getParentInviteCode()).willReturn(expectedResult)

        assertEquals(expectedResult, interactor.getInviteCode())
        verify(userRepository).getParentInviteCode()
    }

    @Test
    fun `getAppVersion() calls userRepository getAppVersion()`() = runBlockingTest {
        val expectedResult = "version"
        given(userRepository.getAppVersion()).willReturn(expectedResult)

        assertEquals(expectedResult, interactor.getAppVersion())
        verify(userRepository).getAppVersion()
    }
}
