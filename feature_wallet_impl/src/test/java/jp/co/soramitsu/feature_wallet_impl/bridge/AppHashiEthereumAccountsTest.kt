package jp.co.soramitsu.feature_wallet_impl.bridge

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.model.WalletBackupMaterial
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wallet's Ethereum account: derived only from a phrase proven to be the account's own. */
class AppHashiEthereumAccountsTest {

    private val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private fun material(words: List<String>) = WalletBackupMaterial(
        canSign = true, phraseWords = words, seedHex = "", storedPhraseDoesNotMatch = false, storedSeedDoesNotMatch = false,
    )

    private val credentials = mockk<CredentialsRepository> {
        coEvery { isExplicitWatchOnly(any()) } returns false
        coEvery { retrieveBackupMaterial(any()) } returns material(phrase.split(" "))
    }

    @Test
    fun `the address is the MetaMask account of the phrase and is cached`() = runBlocking {
        val store = InMemoryStore()
        val accounts = AppHashiEthereumAccounts(credentials, store)
        assertEquals(TestEthAccount.ADDRESS, accounts.address(testAccount))
        assertEquals(TestEthAccount.ADDRESS, accounts.address(testAccount))
        assertEquals(TestEthAccount.ADDRESS, store.ethereumAddress(testAccount.substrateAddress))
        coVerify(exactly = 1) { credentials.retrieveBackupMaterial(testAccount) }
    }

    @Test
    fun `the private key exists only inside the block and is wiped after it`() = runBlocking {
        val accounts = AppHashiEthereumAccounts(credentials, InMemoryStore())
        var seen: ByteArray? = null
        val copy = accounts.withPrivateKey(testAccount, TestEthAccount.ADDRESS) { key ->
            seen = key
            key.copyOf()
        }
        assertTrue(copy.contentEquals(TestEthAccount.privateKey))
        assertTrue("wiped", seen!!.all { it == 0.toByte() })
        // A key that is not the expected account's is never handed out.
        val refused = runCatching {
            accounts.withPrivateKey(testAccount, "0x0000000000000000000000000000000000000001") { error("must not run") }
        }.exceptionOrNull()
        assertEquals("HASHI_ETHEREUM_ACCOUNT_MISMATCH", refused?.message)
    }

    @Test
    fun `no key is derived while the wallet's signing rule refuses (recovery mode, read-only account)`() = runBlocking {
        val accounts = AppHashiEthereumAccounts(credentials, InMemoryStore(), signingRefusal = { "WALLET_RECOVERY_READ_ONLY" })
        val refused = runCatching {
            accounts.withPrivateKey(testAccount, TestEthAccount.ADDRESS) { error("must not run") }
        }.exceptionOrNull()
        assertEquals("WALLET_RECOVERY_READ_ONLY", refused?.message)
        coVerify(exactly = 0) { credentials.retrieveBackupMaterial(any()) }
    }

    @Test
    fun `seed or JSON imports and watch-only accounts have no Ethereum account`() = runBlocking {
        coEvery { credentials.retrieveBackupMaterial(any()) } returns material(emptyList())
        assertNull(AppHashiEthereumAccounts(credentials, InMemoryStore()).address(testAccount))
        coEvery { credentials.isExplicitWatchOnly(any()) } returns true
        assertNull(AppHashiEthereumAccounts(credentials, InMemoryStore()).address(testAccount))
    }
}
