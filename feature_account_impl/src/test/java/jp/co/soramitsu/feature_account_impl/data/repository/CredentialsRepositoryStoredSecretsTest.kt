package jp.co.soramitsu.feature_account_impl.data.repository

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import jp.co.soramitsu.common.account.RecoveryPhrase
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.StoredSecretHex
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialField
import jp.co.soramitsu.feature_account_impl.domain.keys.FakeCredentialsDatasource
import jp.co.soramitsu.feature_account_impl.domain.keys.FakeStoredKeyCrypto
import jp.co.soramitsu.feature_account_impl.domain.keys.toHex
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The credential repository's rules for stored secrets, run against an in-memory preference
 * store and a stand-in for the native sr25519 library (JVM tests cannot load it). The phrase
 * parser, the seed derivation and the address codec are the production ones. The same rules run
 * against the real library and real storage in WalletUpgradeScenarioTest.
 */
class CredentialsRepositoryStoredSecretsTest {

    private val codec = Sora2AddressCodec()
    private val crypto = FakeStoredKeyCrypto()
    private val store = FakeCredentialsDatasource()
    private val json = mockk<JsonAccountsEncoder>()
    private val repository = CredentialsRepositoryImpl(store, mockk<CryptoAssistant>(), codec, json, crypto)

    private val phrase = requireNotNull(RecoveryPhrase.parseOrNull(TWELVE))
    private val keys = crypto.generateKeypair(phrase.miniSeed())
    private val address = requireNotNull(codec.toSoraAddressOrNull(keys.publicKey))
    private val account = SoraAccount(address, "Name")
    private val seedHex = StoredSecretHex.encode(phrase.miniSeed())
    private val fullSeedHex = StoredSecretHex.encode(phrase.fullSeed())

    @Before
    fun normalMode() {
        WalletRecoveryCapabilityGate.enterNormal()
        // Other test classes in the same JVM mock the phrase and seed libraries; this one runs
        // the real ones.
        unmockkAll()
        mockkObject(FirebaseWrapper)
        every { FirebaseWrapper.log(any<String>()) } just runs
    }

    @After
    fun release() {
        unmockkObject(FirebaseWrapper)
    }

    @Test
    fun `a phrase is valid with any BIP39 length and any separators the parser accepts`() = runTest {
        listOf(TWELVE, FIFTEEN, EIGHTEEN, TWENTY_ONE, TWENTY_FOUR).forEach { text ->
            assertTrue(repository.isMnemonicValid(text))
            assertTrue(repository.isMnemonicValid(text.replace(" ", ",")))
            assertTrue(repository.isMnemonicValid(text.replace(" ", ", ")))
            assertEquals(64, repository.convertPassphraseToSeed(text).length)
            assertEquals(
                repository.convertPassphraseToSeed(text),
                repository.convertRetainedSoraPassphraseToSeed(text.replace(" ", ",")),
            )
        }
        assertEquals(seedHex, repository.convertPassphraseToSeed(TWELVE))
        assertEquals(PINNED_TWELVE_SEED, seedHex)
        assertFalse(repository.isMnemonicValid("abandon abandon abandon"))
        assertFalse(repository.isMnemonicValid(""))
    }

    @Test
    fun `a draft stores nothing until it is committed and then writes each value once`() = runTest {
        val draft = repository.draftFromMnemonic(TWELVE.replace(" ", ", "), "Name")
        assertEquals(address, draft.address)
        assertTrue(store.values.isEmpty())

        assertEquals(account, repository.commitDraft(draft))

        assertEquals(1, store.addOnlyWrites.size)
        assertEquals(
            setOf(
                CredentialField.PRIVATE_KEY,
                CredentialField.PUBLIC_KEY,
                CredentialField.KEY_NONCE,
                CredentialField.MNEMONIC,
            ),
            store.addOnlyWrites.single().keys,
        )
        // New phrases are stored in the canonical form.
        assertEquals(TWELVE, store.addOnlyWrites.single()[CredentialField.MNEMONIC])
        assertTrue(store.replacingWrites.isEmpty())
        assertTrue(repository.storedKeyProvesAddress(address))
    }

    @Test
    fun `a raw seed import writes the seed and keys only where absent`() = runTest {
        val draft = repository.draftFromRawSeed("0x$seedHex", "Seed")
        assertEquals(address, draft.address)
        repository.commitDraft(draft)
        assertEquals(seedHex, store.addOnlyWrites.single()[CredentialField.SEED])
        assertFalse(CredentialField.MNEMONIC in store.addOnlyWrites.single())
    }

    @Test
    fun `stored keys that prove the address are adopted unchanged whatever is stored next to them`() =
        runTest {
            val variants: List<FakeCredentialsDatasource.() -> Unit> = listOf(
                // The same phrase stored with commas (2.3.x) and a 64-byte seed cache (3.8.2.0).
                {
                    put(CredentialField.MNEMONIC, address, TWELVE.replace(" ", ","))
                    put(CredentialField.SEED, address, fullSeedHex)
                },
                // Upper-case hex with a 0X prefix.
                { put(CredentialField.SEED, address, "0X" + seedHex.uppercase()) },
                // A phrase and a seed that belong to another wallet.
                {
                    put(CredentialField.MNEMONIC, address, TWENTY_FOUR)
                    put(CredentialField.SEED, address, "11".repeat(32))
                },
                // A seed that cannot be read.
                { markUnreadable(CredentialField.SEED, address) },
            )
            variants.forEach { arrange ->
                store.values.clear()
                store.unreadable.clear()
                store.addOnlyWrites.clear()
                // A nonce that is not the freshly derived one: the key still signs.
                store.putKeys(address, Sr25519Keypair(keys.privateKey, keys.publicKey, ByteArray(32) { 7 }))
                store.arrange()
                val before = store.snapshot()

                val draft = repository.draftFromMnemonic(TWELVE, "Name")
                assertEquals(account, repository.commitDraft(draft))

                // Nothing stored was changed; at most the absent phrase was added.
                before.forEach { (key, value) -> assertEquals(key, value, store.values[key]) }
                assertTrue(store.replacingWrites.isEmpty())
                assertTrue(
                    store.addOnlyWrites.all { it.keys == setOf(CredentialField.MNEMONIC) }
                )
            }
        }

    @Test
    fun `missing values are added when everything stored agrees`() = runTest {
        // Killed between two preference edits: no nonce. The phrase was stored with commas and
        // the seed cache holds the 64-byte value.
        store.put(CredentialField.PRIVATE_KEY, address, keys.privateKey.toHex().uppercase())
        store.put(CredentialField.PUBLIC_KEY, address, "0X" + keys.publicKey.toHex())
        store.put(CredentialField.MNEMONIC, address, TWELVE.replace(" ", ","))
        store.put(CredentialField.SEED, address, fullSeedHex)
        assertFalse(repository.storedKeyProvesAddress(address))
        val before = store.snapshot()

        repository.commitDraft(repository.draftFromMnemonic(TWELVE, "Name"))

        assertEquals(setOf(CredentialField.KEY_NONCE), store.addOnlyWrites.single().keys)
        before.forEach { (key, value) -> assertEquals(key, value, store.values[key]) }
        assertTrue(repository.storedKeyProvesAddress(address))
    }

    @Test
    fun `stored values that differ or cannot be read stop the import and write nothing`() = runTest {
        val other = crypto.generateKeypair(ByteArray(32) { 9 })
        val conflicts: List<FakeCredentialsDatasource.() -> Unit> = listOf(
            // Another wallet's key pair stored under this address.
            { putKeys(address, other) },
            // The private key cannot be read.
            {
                putKeys(address, keys)
                markUnreadable(CredentialField.PRIVATE_KEY, address)
            },
            // No usable key, and the stored phrase is another wallet's.
            {
                put(CredentialField.PUBLIC_KEY, address, keys.publicKey.toHex())
                put(CredentialField.MNEMONIC, address, TWENTY_FOUR)
            },
            // No usable key, and a 64-byte seed whose tail is not this phrase's derivation.
            {
                put(CredentialField.PUBLIC_KEY, address, keys.publicKey.toHex())
                put(CredentialField.SEED, address, seedHex + "00".repeat(32))
            },
            // An explicit watch-only marker.
            { watchOnly += address },
        )
        conflicts.forEach { arrange ->
            store.values.clear()
            store.unreadable.clear()
            store.watchOnly.clear()
            store.arrange()
            val before = store.snapshot()

            val error = runCatching {
                repository.commitDraft(repository.draftFromMnemonic(TWELVE, "Name"))
            }.exceptionOrNull()

            assertEquals(
                ResponseCode.ACCOUNT_STORAGE_CONFLICT,
                (error as? SoraException)?.errorResponseCode,
            )
            assertEquals(before, store.snapshot())
            assertTrue(store.addOnlyWrites.isEmpty() && store.replacingWrites.isEmpty())
        }
    }

    @Test
    fun `repair writes nothing when the copy of the old values cannot be kept`() = runTest {
        val other = crypto.generateKeypair(ByteArray(32) { 9 })
        store.putKeys(address, other)
        val before = store.snapshot()

        // No app context is prepared in a JVM test, so the quarantine copy cannot be made.
        val error = runCatching {
            repository.repairWithDraft(repository.draftFromMnemonic(TWELVE, "Name"))
        }.exceptionOrNull()

        assertEquals(
            ResponseCode.ACCOUNT_REPAIR_COPY_FAILED,
            (error as? SoraException)?.errorResponseCode,
        )
        assertEquals(before, store.snapshot())
        assertTrue(store.replacingWrites.isEmpty())
    }

    @Test
    fun `repair never replaces a stored key that already proves the address`() = runTest {
        store.putKeys(address, Sr25519Keypair(keys.privateKey, keys.publicKey, ByteArray(32) { 7 }))
        store.put(CredentialField.MNEMONIC, address, TWENTY_FOUR)
        val before = store.snapshot()

        assertEquals(account, repository.repairWithDraft(repository.draftFromMnemonic(TWELVE, "Name")))

        assertEquals(before, store.snapshot())
        assertTrue(store.replacingWrites.isEmpty())
    }

    @Test
    fun `the seed shown is the canonical 32 bytes and the stored value stays`() = runTest {
        store.putKeys(address, keys)
        store.put(CredentialField.MNEMONIC, address, TWELVE.replace(" ", ","))
        store.put(CredentialField.SEED, address, fullSeedHex)
        val before = store.snapshot()

        assertEquals(seedHex, repository.retrieveSeed(account))
        val material = repository.retrieveBackupMaterial(account)
        assertTrue(material.canSign)
        assertEquals(TWELVE.split(" "), material.phraseWords)
        assertEquals(seedHex, material.seedHex)
        assertFalse(material.onlyJsonExport)
        // The raw stored text is still what was typed, and nothing was written.
        assertEquals(TWELVE.replace(" ", ","), repository.retrieveMnemonic(account))
        assertEquals(before, store.snapshot())
        assertTrue(store.addOnlyWrites.isEmpty())
    }

    @Test
    fun `a derived seed is cached only where no seed is stored`() = runTest {
        store.putKeys(address, keys)
        store.put(CredentialField.MNEMONIC, address, TWELVE)

        assertEquals(seedHex, repository.retrieveSeed(account))
        assertEquals(mapOf(CredentialField.SEED to seedHex), store.addOnlyWrites.single())
        // A second read finds the cache and writes nothing more.
        assertEquals(seedHex, repository.retrieveSeed(account))
        assertEquals(1, store.addOnlyWrites.size)
    }

    @Test
    fun `fifteen eighteen and twenty one word wallets keep their seed without a cache write`() = runTest {
        listOf(FIFTEEN, EIGHTEEN, TWENTY_ONE).forEach { text ->
            val legacyPhrase = requireNotNull(RecoveryPhrase.parseOrNull(text))
            val legacyKeys = crypto.generateKeypair(legacyPhrase.miniSeed())
            val legacyAddress = requireNotNull(codec.toSoraAddressOrNull(legacyKeys.publicKey))
            store.putKeys(legacyAddress, legacyKeys)
            store.put(CredentialField.MNEMONIC, legacyAddress, text)

            assertEquals(
                StoredSecretHex.encode(legacyPhrase.miniSeed()),
                repository.retrieveSeed(SoraAccount(legacyAddress, "Legacy")),
            )
        }
        assertTrue(store.addOnlyWrites.isEmpty())
    }

    @Test
    fun `material of another wallet is never offered as the backup and JSON export still works`() =
        runTest {
            store.putKeys(address, keys)
            store.put(CredentialField.MNEMONIC, address, TWENTY_FOUR)
            store.put(CredentialField.SEED, address, "11".repeat(32))
            val before = store.snapshot()

            val material = repository.retrieveBackupMaterial(account)
            assertTrue(material.canSign)
            assertTrue(material.onlyJsonExport)
            assertTrue(material.storedPhraseDoesNotMatch && material.storedSeedDoesNotMatch)
            assertTrue(runCatching { repository.retrieveSeed(account) }.isFailure)

            // The sr25519 JSON export encodes the stored key pair; it gets no seed here.
            val exported = slot<JsonAccountsEncoder.ExportAccount>()
            every { json.generate(capture(exported), any(), any()) } returns "json"
            assertEquals("json", repository.generateJson(listOf(account), "password"))
            assertArrayEquals(keys.publicKey, exported.captured.keypair.publicKey)
            assertEquals(0, exported.captured.seed.size)
            assertEquals(before, store.snapshot())
        }

    @Test
    fun `JSON export of several accounts hands over each canonical seed`() = runTest {
        val secondSeed = ByteArray(32) { 0xff.toByte() }
        val secondKeys = crypto.generateKeypair(secondSeed)
        val secondAddress = requireNotNull(codec.toSoraAddressOrNull(secondKeys.publicKey))
        store.putKeys(address, keys)
        store.put(CredentialField.MNEMONIC, address, TWELVE)
        store.put(CredentialField.SEED, address, fullSeedHex)
        store.putKeys(secondAddress, secondKeys)
        store.put(CredentialField.SEED, secondAddress, "0X" + "FF".repeat(32))

        val exported = slot<List<JsonAccountsEncoder.ExportAccount>>()
        every { json.generate(capture(exported), any<String>(), any<String>()) } returns "json"
        repository.generateJson(listOf(account, SoraAccount(secondAddress, "Second")), "password")

        assertArrayEquals(phrase.miniSeed(), exported.captured[0].seed)
        assertArrayEquals(secondSeed, exported.captured[1].seed)
    }

    private companion object {
        const val TWELVE =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val PINNED_TWELVE_SEED = "4ed8d4b17698ddeaa1f1559f152f87b5d472f725ca86d341bd0276f1b61197e2"
        const val FIFTEEN =
            "adapt explain ecology dinner glare old unfair empty expect road off suggest dash " +
                "dolphin case"
        const val EIGHTEEN =
            "fabric expire sight menu twenty pretty piano eager weasel unit name submit buffalo " +
                "lemon drift prefer glide cat"
        const val TWENTY_ONE =
            "adapt explain ecology dinner glare old unfair empty expect road off suggest dash " +
                "dolphin captain version oval census uncover inform apology"
        const val TWENTY_FOUR =
            "abandon amount liar amount expire adjust cage candy arch gather drum bullet absurd " +
                "math era live bid rhythm alien crouch range attend journey unaware"
    }
}
