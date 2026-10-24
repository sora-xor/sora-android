package jp.co.soramitsu.common.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.io.IOException
import jp.co.soramitsu.common.network.BoundedHttpTextException
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Every failure a screen reports ends in a message that says what is known. Before, an
 * exception that was not a [SoraException] showed nothing (a swap closed silently, the referral
 * screen lost its button), and an unexpected one showed "Something went wrong".
 */
class BaseViewModelErrorTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private class Screen : BaseViewModel() {
        var progress = false
        var progressResets = 0

        fun failInBackground(error: Throwable) = onBackgroundError(error)

        override fun onErrorResetProgress() {
            progressResets += 1
            progress = false
        }
    }

    private val generic = R.string.failure_generic_title to R.string.failure_generic_message
    private val accountReadOnly = R.string.account_read_only_title to R.string.account_read_only_refused

    @Test
    fun `an exception that is not a SoraException gets the calm generic message`() {
        val screen = Screen()

        screen.onError(IllegalStateException("anything"))

        assertEquals(generic, screen.errorFromResourceLiveData.value)
    }

    @Test
    fun `the signing gate's refusal says that this account cannot send and that nothing was sent`() {
        val screen = Screen()

        screen.onError(IllegalStateException(FailureNotice.SIGNING_WALLET_UNAVAILABLE))

        assertEquals(accountReadOnly, screen.errorFromResourceLiveData.value)
    }

    @Test
    fun `the refusal is recognised when a screen wrapped it as an unexpected error`() {
        // Send wraps every failure of a submit (TransferAmountViewModel.onConfirmClick).
        val screen = Screen()

        screen.onError(
            SoraException.unexpectedError(
                IllegalStateException(FailureNotice.SIGNING_WALLET_UNAVAILABLE)
            )
        )

        assertEquals(accountReadOnly, screen.errorFromResourceLiveData.value)
    }

    @Test
    fun `an unexpected error never ends in Something went wrong`() {
        val screen = Screen()

        screen.onError(SoraException.unexpectedError(RuntimeException("boom")))

        val shown = requireNotNull(screen.errorFromResourceLiveData.value)
        assertEquals(generic, shown)
        assertFalse(shown.first == R.string.something_went_wrong)
        assertFalse(shown.second == R.string.unexpected_error)
    }

    @Test
    fun `read-only mode of the whole app is named as such`() {
        val screen = Screen()

        screen.onError(IllegalStateException(FailureNotice.WALLET_RECOVERY_READ_ONLY))

        assertEquals(
            R.string.safe_mode_read_only_title to R.string.safe_mode_read_only_body,
            screen.errorFromResourceLiveData.value,
        )
    }

    @Test
    fun `a failed request says to check the connection`() {
        val screen = Screen()

        screen.onError(RuntimeException("wrapped", BoundedHttpTextException("BOUNDED_HTTP_IO")))

        assertEquals(
            R.string.common_error_general_title to R.string.common_error_network,
            screen.errorFromResourceLiveData.value,
        )
    }

    @Test
    fun `a submission whose outcome is unknown says to check Activity and not to try again later`() {
        // Swap, liquidity and referral pass ExtrinsicSubmissionUnknownException to onError as
        // it is. Its cause is the network error that hid the answer.
        val screen = Screen().apply { progress = true }

        screen.onError(
            IllegalStateException(
                FailureNotice.EXTRINSIC_SUBMISSION_UNKNOWN,
                BoundedHttpTextException("BOUNDED_HTTP_DEADLINE"),
            )
        )

        assertEquals(
            R.string.failure_submission_unknown_title to R.string.failure_submission_unknown_message,
            screen.errorFromResourceLiveData.value,
        )
        assertEquals(false, screen.progress)
    }

    @Test
    fun `a storage failure does not send anyone to check the internet connection`() {
        val screen = Screen()

        // Saving the PIN on a full disk (PinCodeViewModel.registerPinCode).
        screen.onError(IOException("No space left on device"))

        assertEquals(
            R.string.failure_generic_title to R.string.failure_generic_message,
            screen.errorFromResourceLiveData.value,
        )
    }

    @Test
    fun `a failure with its own message keeps it`() {
        val screen = Screen()

        screen.onError(SoraException.businessError(ResponseCode.WRONG_PIN_CODE))

        assertEquals(
            ResponseCode.WRONG_PIN_CODE.titleResource to ResponseCode.WRONG_PIN_CODE.messageResource,
            screen.errorFromResourceLiveData.value,
        )
    }

    @Test
    fun `a business error without a text still says something`() {
        val screen = Screen()

        screen.onError(SoraException.businessError(ResponseCode.SECTION_NOT_FOUND))

        assertEquals(generic, screen.errorFromResourceLiveData.value)
    }

    @Test
    fun `every reported failure puts the screen's controls back`() {
        val screen = Screen().apply { progress = true }

        screen.onError(IllegalStateException(FailureNotice.SIGNING_WALLET_UNAVAILABLE))

        assertFalse(screen.progress)
        assertEquals(1, screen.progressResets)

        screen.progress = true
        screen.onError(SoraException.businessError(ResponseCode.WRONG_PIN_CODE))
        assertFalse(screen.progress)
    }

    @Test
    fun `a cancelled coroutine is not a failure`() {
        val screen = Screen().apply { progress = true }

        screen.onError(CancellationException("left the screen"))

        assertNull(screen.errorFromResourceLiveData.value)
        assertNull(screen.errorLiveData.value)
        assertNull(screen.alertDialogLiveData.value)
        assertTrue(screen.progress)
    }

    @Test
    fun `background work that fails opens no dialog and resets nothing`() {
        val screen = Screen().apply { progress = true }

        screen.failInBackground(IOException("offline"))
        screen.failInBackground(IllegalStateException("anything"))

        assertNull(screen.errorFromResourceLiveData.value)
        assertTrue(screen.progress)
        assertEquals(0, screen.progressResets)
    }

    @Test
    fun `background work still shows a failure that carries its own message`() {
        val screen = Screen()

        screen.failInBackground(SoraException.businessError(ResponseCode.WRONG_PIN_CODE))

        assertEquals(
            ResponseCode.WRONG_PIN_CODE.titleResource to ResponseCode.WRONG_PIN_CODE.messageResource,
            screen.errorFromResourceLiveData.value,
        )
    }
}
