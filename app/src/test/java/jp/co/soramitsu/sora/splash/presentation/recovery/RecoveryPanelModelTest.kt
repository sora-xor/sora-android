package jp.co.soramitsu.sora.splash.presentation.recovery

import jp.co.soramitsu.core_db.RecoveryFacts
import jp.co.soramitsu.core_db.SetUpAgainScope
import jp.co.soramitsu.core_db.WalletRecoveryOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Design spec 9.1-1: every blocking state renders its actions in the 9.2 order, offers each one
 * only when its flag says it works, and always has a forward path besides "Try again".
 */
class RecoveryPanelModelTest {

    private val richFacts = RecoveryFacts(
        hasWalletFiles = true,
        hasRecoveryPhraseOrSeed = true,
        databaseAtCurrentVersion = true,
        accountsReadableReadOnly = true,
    )

    private val blockingCodes = listOf(
        "WALLET_MIGRATION_RECOVERY_REQUIRED",
        "MIGRATION_RETRY_AUTHORIZATION_MISMATCH",
        "VERIFIED_RECEIPT_INVALID",
        "DATABASE_CORRUPT",
        "PREFERENCES_INVENTORY_INVALID",
        "INVALID_DATABASE_VERSION",
        "DATABASE_DOWNGRADE_UNSUPPORTED",
        "WALLET_DELETION_JOURNAL_INVALID",
        "KEYSTORE_KEY_UNAVAILABLE",
        "KEYSTORE_UNAVAILABLE",
        "STORAGE_IO",
        "DATABASE_LOCKED",
        "SET_UP_AGAIN_INCOMPLETE",
        "INSUFFICIENT_BACKUP_STORAGE",
        "STORAGE_FULL",
        "SOMETHING_NEW",
    )

    private val factVariants = listOf(
        RecoveryFacts(),
        richFacts,
        richFacts.copy(hasRecoveryPhraseOrSeed = false),
        richFacts.copy(accountsReadableReadOnly = false, databaseAtCurrentVersion = false),
        RecoveryFacts(hasWalletFiles = false),
    )

    @Test
    fun `every blocking state has a forward action besides try again`() {
        for (code in blockingCodes) for (facts in factVariants) for (transient in listOf(false, true)) {
            for (drive in listOf(false, true)) {
                val options = WalletRecoveryOptions.forState(code, transient, facts)
                val model = RecoveryPanels.model(options, driveAvailable = drive)
                assertEquals(RecoveryAction.TRY_AGAIN, model.primary)
                assertTrue("$code $facts", model.forwardActionsBesidesRetry.isNotEmpty())
                assertTrue("$code: support is always offered", RecoveryAction.CONTACT_SUPPORT in model.actions)
                assertEquals("no action twice", model.actions.size, model.actions.toSet().size)
            }
        }
    }

    @Test
    fun `actions follow the flags and the spec order`() {
        val options = WalletRecoveryOptions.forState("WALLET_MIGRATION_RECOVERY_REQUIRED", false, richFacts)
        val model = RecoveryPanels.model(options, driveAvailable = true)
        assertEquals(RecoveryPanelKind.RECOVERY, model.kind)
        assertEquals(listOf(RecoveryAction.RESTORE_PHRASE, RecoveryAction.RESTORE_DRIVE), model.secondary)
        assertEquals(
            listOf(
                RecoveryAction.SHOW_PHRASE,
                RecoveryAction.READ_ONLY,
                RecoveryAction.EXPORT,
                RecoveryAction.CONTACT_SUPPORT,
            ),
            model.tertiary,
        )
        assertEquals(SetUpAgainScope.DATABASE_ONLY, model.setUpAgainScope)
        assertTrue(model.canShowPhrase)
    }

    @Test
    fun `drive restore is hidden without google services`() {
        val options = WalletRecoveryOptions.forState("DATABASE_CORRUPT", false, richFacts)
        val model = RecoveryPanels.model(options, driveAvailable = false)
        assertFalse(RecoveryAction.RESTORE_DRIVE in model.actions)
        assertTrue(RecoveryAction.RESTORE_PHRASE in model.actions)
    }

    @Test
    fun `read only and phrase are not offered when the key cannot be used`() {
        val options = WalletRecoveryOptions.forState("KEYSTORE_KEY_UNAVAILABLE", false, richFacts)
        val model = RecoveryPanels.model(options, driveAvailable = true)
        assertEquals(RecoveryPanelKind.KEYS_UNAVAILABLE, model.kind)
        assertFalse(RecoveryAction.READ_ONLY in model.actions)
        assertFalse(RecoveryAction.SHOW_PHRASE in model.actions)
        assertTrue(RecoveryAction.RESTORE_PHRASE in model.actions)
        assertEquals(SetUpAgainScope.ALL_WALLET_FILES, model.setUpAgainScope)
    }

    @Test
    fun `transient states retry by themselves and never offer restore`() {
        val options = WalletRecoveryOptions.forState("KEYSTORE_UNAVAILABLE", true, richFacts)
        val model = RecoveryPanels.model(options, driveAvailable = true)
        assertEquals(RecoveryPanelKind.TRANSIENT, model.kind)
        assertTrue(model.retryAutomatically)
        assertTrue(model.secondary.isEmpty())
        assertFalse(RecoveryAction.READ_ONLY in model.actions)
    }

    @Test
    fun `storage screen shows the space needed and storage settings, never export`() {
        val options = WalletRecoveryOptions.forState(
            code = "INSUFFICIENT_BACKUP_STORAGE",
            transientFailure = false,
            facts = richFacts,
            requiredBytes = 120L * 1_048_576 + 1,
            availableBytes = 40L * 1_048_576,
        )
        val model = RecoveryPanels.model(options, driveAvailable = true)
        assertEquals(RecoveryPanelKind.STORAGE, model.kind)
        assertEquals(121L, model.requiredMegabytes)
        assertEquals(81L, model.missingMegabytes)
        assertEquals(RecoveryAction.OPEN_STORAGE, model.tertiary.first())
        assertFalse(RecoveryAction.EXPORT in model.actions)
        assertTrue(model.secondary.isEmpty())
    }

    @Test
    fun `unknown free space uses the generic storage text`() {
        val options = WalletRecoveryOptions.forState("STORAGE_FULL", true, richFacts)
        val model = RecoveryPanels.model(options, driveAvailable = true)
        assertEquals(RecoveryPanelKind.STORAGE, model.kind)
        assertEquals(null, model.requiredMegabytes)
    }

    @Test
    fun `an interrupted removal says so instead of claiming nothing changed`() {
        val model = RecoveryPanels.model(
            WalletRecoveryOptions.forState("WALLET_DELETION_JOURNAL_INVALID", false, richFacts),
            driveAvailable = true,
        )
        assertTrue(model.interruptedRemoval)
        val other = RecoveryPanels.model(
            WalletRecoveryOptions.forState("DATABASE_CORRUPT", false, richFacts),
            driveAvailable = true,
        )
        assertFalse(other.interruptedRemoval)
    }
}
