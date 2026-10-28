package jp.co.soramitsu.sora.splash

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.sora.debug.SafeModePreview
import jp.co.soramitsu.sora.debug.SafeModePreviewData
import jp.co.soramitsu.sora.debug.SafeModePreviewState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import jp.co.soramitsu.common.R as CommonR
import jp.co.soramitsu.sora.R

/** Preview scenarios are QA entry points, and must retain their recovery and privacy meaning. */
@RunWith(Parameterized::class)
class SafeModePreviewContractTest(private val preview: SafeModePreviewState, private val expected: Int) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun rendersExpectedRecoveryStateWithoutUnintendedSecretDisclosure() {
        compose.setContent { SoraAppTheme { SafeModePreview(preview) } }
        compose.onAllNodesWithText(compose.activity.getString(expected)).onFirst().assertExists()
        if (preview == SafeModePreviewState.U6_REVEAL_SHOWN) {
            compose.onNodeWithText(SafeModePreviewData.samplePhrase.first()).assertExists()
        } else {
            compose.onAllNodesWithText(SafeModePreviewData.samplePhrase.first()).assertCountEquals(0)
        }
        if (preview == SafeModePreviewState.U11_UPGRADE_COMPLETE) {
            compose.onNodeWithText(compose.activity.getString(CommonR.string.safe_mode_back_up_now)).assertExists()
        } else if (preview == SafeModePreviewState.U11_UPGRADE_COMPLETE_BACKED_UP) {
            compose.onNodeWithText(compose.activity.getString(CommonR.string.safe_mode_back_up_now)).assertDoesNotExist()
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun cases() = listOf(
            arrayOf<Any>(SafeModePreviewState.U1_PIN_CHECK, CommonR.string.safe_mode_checking_title),
            arrayOf<Any>(SafeModePreviewState.U5_FINISHING, CommonR.string.safe_mode_finishing_title),
            arrayOf<Any>(SafeModePreviewState.U9_READ_ONLY_HOME, CommonR.string.safe_mode_read_only_title),
            arrayOf<Any>(SafeModePreviewState.U9_LEARN_MORE, CommonR.string.safe_mode_read_only_details),
            arrayOf<Any>(SafeModePreviewState.U10_NEW_PIN, CommonR.string.safe_mode_new_pin_title),
            arrayOf<Any>(SafeModePreviewState.U11_UPGRADE_COMPLETE, CommonR.string.safe_mode_whats_new_title),
            arrayOf<Any>(SafeModePreviewState.U11_UPGRADE_COMPLETE_BACKED_UP, CommonR.string.safe_mode_whats_new_title),
            arrayOf<Any>(SafeModePreviewState.U12_DEVICE_TRANSFER, CommonR.string.safe_mode_device_transfer),
            arrayOf<Any>(SafeModePreviewState.U13_PENDING_CHECK, CommonR.string.safe_mode_pending_check),
            arrayOf<Any>(SafeModePreviewState.U6_REVEAL_PIN, R.string.recovery_auth_title),
            arrayOf<Any>(SafeModePreviewState.U6_REVEAL_WRONG_PIN, R.string.recovery_auth_wrong),
            arrayOf<Any>(SafeModePreviewState.U6_REVEAL_VEILED, R.string.recovery_phrase_title),
            arrayOf<Any>(SafeModePreviewState.U6_REVEAL_SHOWN, R.string.recovery_phrase_title),
            arrayOf<Any>(SafeModePreviewState.U6_REVEAL_UNREADABLE, R.string.recovery_phrase_unreadable),
        )
    }
}
