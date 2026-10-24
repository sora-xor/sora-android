/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause
*/

package jp.co.soramitsu.common.presentation.transaction

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.io.IOException
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SigningKeyUnavailableException
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.common.util.WalletDecryptionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

/**
 * Audit A5: a confirm button never ends in silence, and the "Not sent" text says what is true.
 * Nothing was sent in any of these cases; the reason decides whether trying again can help.
 */
class TransactionNotSentTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    private val resources: ResourceManager = mock(ResourceManager::class.java).also { mocked ->
        given(mocked.getString(anyInt())).willAnswer { "text-${it.getArgument<Int>(0)}" }
    }

    private class Screen(private val resources: ResourceManager) : BaseViewModel() {
        fun confirmFailedWith(error: Throwable) = showTransactionNotSent(resources, error)
    }

    private fun notSentMessage(error: Throwable): String =
        TransactionOutcomeTexts.notSent(resources, error).message

    private fun text(id: Int) = "text-$id"

    @Test
    fun `an unreachable network asks to check the connection and try again`() {
        assertEquals(
            text(R.string.tx_outcome_not_sent_network),
            notSentMessage(IllegalStateException("wrapped", IOException("BOUNDED_HTTP_IO"))),
        )
    }

    @Test
    fun `a changed fee asks to review the new fee`() {
        assertEquals(
            text(R.string.tx_outcome_not_sent_fee_changed),
            notSentMessage(
                IllegalStateException(
                    "EXTRINSIC_PRE_TRANSPORT_VALIDATION_FAILED",
                    IllegalStateException("SORA2_TRANSFER_FEE_CHANGED"),
                )
            ),
        )
    }

    @Test
    fun `a balance that is no longer enough is named and is not told to try again`() {
        // AssetsRepositoryImpl.requireExactTransferBalances, at the last check before transport,
        // against the balance the network holds at that moment. Before: "This transaction
        // couldn't be prepared ... Please try again", which cannot help.
        listOf(
            "SORA2_TRANSFER_XOR_BALANCE_INSUFFICIENT",
            "SORA2_TRANSFER_ASSET_BALANCE_INSUFFICIENT",
        ).forEach { code ->
            assertEquals(
                text(R.string.tx_outcome_not_sent_balance),
                notSentMessage(
                    IllegalStateException(
                        "EXTRINSIC_PRE_TRANSPORT_VALIDATION_FAILED",
                        IllegalStateException(code),
                    )
                ),
            )
        }
    }

    @Test
    fun `an account that may not sign is not told to try again`() {
        // WalletSigningGate.unavailableReason, thrown by the signer before anything is built.
        listOf("SIGNING_WALLET_UNAVAILABLE", "WALLET_DELETION_ACTIVE").forEach { reason ->
            assertEquals(
                text(R.string.tx_outcome_not_sent_account_unavailable),
                notSentMessage(IllegalStateException(reason)),
            )
            // The same refusal at the last check before transport arrives wrapped.
            assertEquals(
                text(R.string.tx_outcome_not_sent_account_unavailable),
                notSentMessage(
                    IllegalStateException(
                        "EXTRINSIC_PRE_TRANSPORT_VALIDATION_FAILED",
                        IllegalStateException(reason),
                    )
                ),
            )
        }
    }

    @Test
    fun `read-only mode after an unfinished upgrade check says so`() {
        assertEquals(
            text(R.string.safe_mode_read_only_details),
            notSentMessage(IllegalStateException("WALLET_RECOVERY_READ_ONLY")),
        )
    }

    @Test
    fun `the signer refusing is not mistaken for a network problem`() {
        // The cause chain of a refusal can end in an I/O error of an earlier step.
        assertEquals(
            text(R.string.tx_outcome_not_sent_account_unavailable),
            notSentMessage(IllegalStateException("SIGNING_WALLET_UNAVAILABLE", IOException("io"))),
        )
    }

    @Test
    fun `an account without a readable signing key is not told to try again`() {
        // Before: "This transaction couldn't be prepared ... Please try again", on every try,
        // and nothing said that this account cannot sign on this phone.
        listOf(
            // CredentialsRepositoryImpl.retrieveKeyPair: no signing key is stored.
            SigningKeyUnavailableException("Keypair not found"),
            // The stored key cannot be decrypted (its bytes, not the device state).
            WalletDecryptionException("Authenticated wallet envelope failed verification"),
            // The Keystore alias is gone while encrypted wallet material still exists.
            KeyMaterialUnavailableException("alias missing", transient = false),
        ).forEach { error ->
            assertEquals(
                text(R.string.tx_outcome_not_sent_key_unavailable),
                notSentMessage(error),
            )
            // As it reaches a screen through an interactor or a submit helper.
            assertEquals(
                text(R.string.tx_outcome_not_sent_key_unavailable),
                notSentMessage(RuntimeException("confirm failed", error)),
            )
        }
    }

    @Test
    fun `a Keystore that is only busy keeps the try again text`() {
        // Transient: a later attempt may succeed, so it is never reported as a lost key.
        val busy = KeyMaterialUnavailableException(
            "Android Keystore is temporarily unavailable",
            transient = true,
        )
        assertEquals(text(R.string.tx_outcome_not_sent_generic), notSentMessage(busy))
        // Also when a decryption error was raised on top of the busy Keystore.
        assertEquals(
            text(R.string.tx_outcome_not_sent_generic),
            notSentMessage(WalletDecryptionException("Unable to decrypt", busy)),
        )
    }

    @Test
    fun `any other error says nothing was sent`() {
        assertEquals(
            text(R.string.tx_outcome_not_sent_generic),
            notSentMessage(IllegalArgumentException("bad amount")),
        )
        assertEquals(
            text(R.string.tx_outcome_not_sent_title),
            TransactionOutcomeTexts.notSent(resources, IllegalArgumentException("x")).title,
        )
    }

    @Test
    fun `a self-referencing cause chain terminates`() {
        val error = object : RuntimeException("loop") {
            override val cause: Throwable get() = this
        }
        assertEquals(text(R.string.tx_outcome_not_sent_generic), notSentMessage(error))
    }

    // --- the dialog every confirm screen shows -------------------------------------------------

    @Test
    fun `an error with no message of its own still gets the Not sent dialog`() {
        // onError shows nothing for these; before, the confirm button ended in silence.
        listOf(
            SoraException(SoraException.Kind.UNEXPECTED, "boom"),
            SoraException(SoraException.Kind.BUSINESS, "no response code"),
        ).forEach { silent ->
            val screen = Screen(resources)

            screen.confirmFailedWith(silent)

            assertEquals(
                text(R.string.tx_outcome_not_sent_title) to
                    text(R.string.tx_outcome_not_sent_generic),
                screen.alertDialogLiveData.value,
            )
        }
    }

    @Test
    fun `a generic network or unexpected error says nothing was sent`() {
        // onError would show "No network" or "Unexpected error" without saying what happened
        // to the transaction.
        val network = Screen(resources)
        network.confirmFailedWith(SoraException.networkError(resources, IOException("offline")))
        assertEquals(
            text(R.string.tx_outcome_not_sent_title) to text(R.string.tx_outcome_not_sent_network),
            network.alertDialogLiveData.value,
        )
        assertNull(network.errorLiveData.value)

        val unexpected = Screen(resources)
        unexpected.confirmFailedWith(SoraException.unexpectedError(IllegalStateException("x")))
        assertEquals(
            text(R.string.tx_outcome_not_sent_title) to text(R.string.tx_outcome_not_sent_generic),
            unexpected.alertDialogLiveData.value,
        )
        assertNull(unexpected.errorFromResourceLiveData.value)
    }

    @Test
    fun `a business error keeps its own specific message`() {
        val screen = Screen(resources)

        screen.confirmFailedWith(SoraException.businessError(ResponseCode.GENERAL_ERROR))

        assertEquals(
            ResponseCode.GENERAL_ERROR.titleResource to ResponseCode.GENERAL_ERROR.messageResource,
            screen.errorFromResourceLiveData.value,
        )
        assertNull(screen.alertDialogLiveData.value)
    }

    @Test
    fun `any other failure gets the Not sent dialog`() {
        val screen = Screen(resources)

        screen.confirmFailedWith(IllegalStateException("SIGNING_WALLET_UNAVAILABLE"))

        assertEquals(
            text(R.string.tx_outcome_not_sent_title) to
                text(R.string.tx_outcome_not_sent_account_unavailable),
            screen.alertDialogLiveData.value,
        )
    }
}
