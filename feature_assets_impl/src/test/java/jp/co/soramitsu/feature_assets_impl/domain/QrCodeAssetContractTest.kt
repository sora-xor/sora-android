package jp.co.soramitsu.feature_assets_impl.domain

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.util.QrException
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

class QrCodeAssetContractTest {
    private val assets = mock<AssetsRepository>()
    private val users = mock<UserRepository>()
    private val runtime = mock<RuntimeManager>()
    private val interactor = QrCodeInteractorImpl(assets, users, runtime)

    private suspend fun validRecipient() {
        given(users.getCurSoraAccount()).willReturn(SoraAccount("sender", "Sender"))
        given(runtime.isAddressOk("recipient")).willReturn(true)
    }

    @Test
    fun `unknown QR assets never become a transfer of XOR`() = runTest {
        validRecipient()
        given(assets.isWhitelistedToken("unknown-token")).willReturn(false)
        val error = runCatching {
            interactor.processQrResult("substrate:recipient:key:Recipient:unknown-token:500")
        }.exceptionOrNull()
        assertTrue("Unexpected failure: $error", error is QrException && error.kind == QrException.Kind.DECODE_ERROR)
    }

    @Test
    fun `empty QR asset never becomes a transfer of XOR`() = runTest {
        validRecipient()
        val error = runCatching {
            interactor.processQrResult("substrate:recipient:key:Recipient::1")
        }.exceptionOrNull()
        assertTrue(error is QrException && error.kind == QrException.Kind.DECODE_ERROR)
        verifyNoInteractions(assets)
    }

    @Test
    fun `supported token and exact amount survive creation and decoding`() = runTest {
        validRecipient()
        given(assets.isWhitelistedToken("token")).willReturn(true)
        for (amount in listOf(null, "0.000000000000000001", "9007199254740993.123456789012345678")) {
            val encoded = interactor.createQrInput("recipient", "key", "Recipient", "token", amount)
            assertEquals(Triple("recipient", "token", amount), interactor.processQrResult(encoded))
        }
    }

    @Test
    fun `creation defaults to the fee asset only when no asset was supplied`() = runTest {
        assertEquals("substrate:recipient:key:Recipient:${SubstrateOptionsProvider.feeAssetId}",
            interactor.createQrInput("recipient", "key", "Recipient", null, null))
        assertEquals("substrate:recipient:key:Recipient:token:1.5",
            interactor.createQrInput("recipient", "key", "Recipient", "token", "1.5"))
        verifyNoInteractions(assets, users, runtime)
    }

    @Test
    fun `malformed envelopes are rejected before account and network lookups`() = runTest {
        for (input in listOf("", "substrate::key:name:token", "substrate:recipient:key:name", "other:recipient:key:name:token",
            "substrate:recipient:key:name:token:1:extra")) {
            val error = runCatching { interactor.processQrResult(input) }.exceptionOrNull()
            assertTrue(input, error is QrException && error.kind == QrException.Kind.DECODE_ERROR)
        }
        verifyNoInteractions(assets, users, runtime)
    }

    @Test
    fun `cancellation from token lookup remains cancellation`() = runTest {
        validRecipient()
        val cancellation = CancellationException("screen closed")
        given(assets.isWhitelistedToken("token")).willThrow(cancellation)
        val error = runCatching {
            interactor.processQrResult("substrate:recipient:key:Recipient:token:1")
        }.exceptionOrNull()
        assertSame(cancellation, error)
    }
}
