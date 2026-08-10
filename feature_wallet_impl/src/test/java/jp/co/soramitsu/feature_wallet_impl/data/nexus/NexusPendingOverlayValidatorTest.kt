package jp.co.soramitsu.feature_wallet_impl.data.nexus

import jp.co.soramitsu.common.nexus.IrohaAddressCodec
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.nexus.WalletNetworkChainIdentity
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusPendingOverlayValidatorTest {

    private val network = NexusNetworks.minamoto
    private val recipient = IrohaAddressCodec.encode(
        ByteArray(32) { index -> (index + 1).toByte() },
        network.chainDiscriminant,
    )

    @Test
    fun `pending overlay is bound to exact wallet network and durable state`() {
        val pending = pending()

        NexusPendingOverlayValidator.requireValid(
            transaction = pending,
            expectedWalletId = pending.walletId,
            expectedNetworkId = pending.networkId,
            expectedChainId = network.chainId,
            discriminant = network.chainDiscriminant,
        )

        listOf(
            pending.copy(walletId = "another-wallet"),
            pending.copy(networkId = "taira"),
            pending.copy(submissionIsAmbiguous = true),
            pending.copy(updatedAt = pending.createdAt - 1),
            pending.copy(assetId = "xor#universal"),
            pending.copy(transactionHash = "0x" + "ab".repeat(32)),
            pending.copy(transactionHash = "0".repeat(64)),
            pending.copy(amount = "1.0"),
            pending.copy(amount = "0"),
            pending.copy(createdAt = 0, updatedAt = 0),
        ).forEach { invalid ->
            assertThrows(IllegalStateException::class.java) {
                NexusPendingOverlayValidator.requireValid(
                    transaction = invalid,
                    expectedWalletId = pending.walletId,
                    expectedNetworkId = pending.networkId,
                    expectedChainId = network.chainId,
                    discriminant = network.chainDiscriminant,
                )
            }
        }
    }

    @Test
    fun `ambiguous overlay is valid only in unknown state`() {
        val unknown = pending().copy(
            state = "UNKNOWN",
            submissionIsAmbiguous = true,
        )

        NexusPendingOverlayValidator.requireValid(
            transaction = unknown,
            expectedWalletId = unknown.walletId,
            expectedNetworkId = unknown.networkId,
            expectedChainId = network.chainId,
            discriminant = network.chainDiscriminant,
        )
        assertThrows(IllegalStateException::class.java) {
            NexusPendingOverlayValidator.requireValid(
                transaction = unknown.copy(submissionIsAmbiguous = false),
                expectedWalletId = unknown.walletId,
                expectedNetworkId = unknown.networkId,
                expectedChainId = network.chainId,
                discriminant = network.chainDiscriminant,
            )
        }
    }

    @Test
    fun `corrupt durable pending batch becomes recovery evidence without unsafe projection`() {
        val valid = pending()
        val projected = NexusPendingOverlayValidator.projectForPortfolio(
            transactions = listOf(valid),
            expectedWalletId = valid.walletId,
            expectedNetworkId = valid.networkId,
            expectedChainId = network.chainId,
            discriminant = network.chainDiscriminant,
        )
        assertTrue(projected is NexusPendingOverlayValidator.Projection.Valid)
        assertEquals(
            XOR_DEFINITION_ID,
            (projected as NexusPendingOverlayValidator.Projection.Valid)
                .transactions.single().assetDefinitionId,
        )

        val recovery = NexusPendingOverlayValidator.projectForPortfolio(
            transactions = listOf(valid, valid.copy(transactionHash = null)),
            expectedWalletId = valid.walletId,
            expectedNetworkId = valid.networkId,
            expectedChainId = network.chainId,
            discriminant = network.chainDiscriminant,
        )
        assertTrue(recovery is NexusPendingOverlayValidator.Projection.RecoveryRequired)
        val recoveryAccess =
            NexusPendingJournalPresentationContract.portfolioAccess(
                recoveryRequired = true
            )
        assertEquals(true, recoveryAccess.allowsQualifiedReads)
        assertEquals(false, recoveryAccess.projectsPending)
        assertEquals(false, recoveryAccess.allowsSends)
        val normalAccess =
            NexusPendingJournalPresentationContract.portfolioAccess(
                recoveryRequired = false
            )
        assertEquals(true, normalAccess.allowsQualifiedReads)
        assertEquals(true, normalAccess.projectsPending)
        assertEquals(true, normalAccess.allowsSends)
    }

    @Test
    fun `unbound and retired chain rows project only as recovery evidence`() {
        listOf<String?>(null, "809574f5-fee7-5e69-bfcf-52451e42d50f").forEach { chainId ->
            val historical = pending().copy(chainId = chainId)
            assertTrue(
                NexusPendingOverlayValidator.projectForPortfolio(
                    transactions = listOf(historical),
                    expectedWalletId = historical.walletId,
                    expectedNetworkId = historical.networkId,
                    expectedChainId = network.chainId,
                    discriminant = network.chainDiscriminant,
                ) is NexusPendingOverlayValidator.Projection.RecoveryRequired
            )
            assertEquals(
                true,
                NexusPendingJournalPresentationContract.requiresRecovery(
                    transactions = listOf(historical),
                    networkAccounts = listOf(networkAccount()),
                    maximumTransactions = 500,
                ),
            )
        }
    }

    @Test
    fun `Taira current identity requires exact manifest namespace for either mapping`() {
        val epochA = "809574f5-fee7-5e69-bfcf-52451e42d50f"
        val epochB = "fc56984b-2be7-431d-840e-21514d1883f0"
        val manifestA = "a".repeat(64)
        val manifestB = "b".repeat(64)
        val prefixA = "taira:$manifestA:"
        val prefixB = "taira:$manifestB:"
        val currentA = pending().copy(
            localId = "${prefixA}journal-a",
            networkId = "taira",
            chainId = epochA,
        )
        val currentB = pending().copy(
            localId = "${prefixB}journal-b",
            networkId = "taira",
            chainId = epochB,
        )

        assertTrue(
            WalletIdentityDao.hasCurrentChainIdentityForBinding(currentA, epochA, prefixA)
        )
        assertTrue(
            WalletIdentityDao.hasCurrentChainIdentityForBinding(currentB, epochB, prefixB)
        )
        listOf(
            currentA.copy(localId = "legacy-same-uuid"),
            currentA.copy(localId = "${prefixB}other-manifest"),
            currentA.copy(chainId = epochB),
        ).forEach { retained ->
            assertEquals(
                false,
                WalletIdentityDao.hasCurrentChainIdentityForBinding(
                    retained,
                    epochA,
                    prefixA,
                ),
            )
        }
    }

    @Test
    fun `shared pending journal never hides an invalid domain or overflow row`() {
        val nexus = pending()
        val polkamarkt = nexus.copy(
            localId = "polkamarkt-id",
            networkId = "sora2",
            chainId = WalletNetworkChainIdentity.SORA2,
            transactionHash = "0x" + "cd".repeat(32),
            assetId = "polkamarkt:7:BUY",
            recipient = "Yes",
        )
        assertEquals(
            false,
            NexusPendingJournalPresentationContract.requiresRecovery(
                listOf(nexus, polkamarkt),
                networkAccounts = listOf(networkAccount()),
                maximumTransactions = 500,
            ),
        )
        listOf(
            nexus.copy(networkId = "unknown"),
            polkamarkt.copy(assetId = "polkamarkt:7:buy"),
            polkamarkt.copy(transactionHash = "cd".repeat(32)),
        ).forEach { invalid ->
            assertEquals(
                true,
                NexusPendingJournalPresentationContract.requiresRecovery(
                    listOf(nexus, invalid),
                    networkAccounts = listOf(networkAccount()),
                    maximumTransactions = 500,
                ),
            )
        }
        assertEquals(
            true,
            NexusPendingJournalPresentationContract.requiresRecovery(
                List(501) { nexus },
                networkAccounts = listOf(networkAccount()),
                maximumTransactions = 500,
            ),
        )
        assertEquals(
            true,
            NexusPendingJournalPresentationContract.requiresRecovery(
                listOf(nexus),
                networkAccounts = emptyList(),
                maximumTransactions = 500,
            ),
        )
    }

    private fun networkAccount() = NetworkAccountLocal(
        walletId = "wallet-id",
        networkId = network.id.wireId,
        publicKey = IrohaAddressCodec.parse(
            recipient,
            network.chainDiscriminant,
        ).publicKeyHex,
        address = recipient,
        derivationPath = "m/44'/617'/0'/0'",
        derivationVersion = 1,
        enabled = true,
    )

    private fun pending() = PendingNetworkTransactionLocal(
        localId = "local-id",
        walletId = "wallet-id",
        networkId = network.id.wireId,
        chainId = network.chainId,
        transactionHash = "ab".repeat(32),
        assetId = XOR_DEFINITION_ID,
        amount = "1.25",
        recipient = recipient,
        state = "SIGNED",
        submissionIsAmbiguous = false,
        createdAt = 1_000,
        updatedAt = 1_000,
    )

    private companion object {
        const val XOR_DEFINITION_ID = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"
    }
}
