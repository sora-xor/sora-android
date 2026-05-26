package jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.PACKAGE_ID
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BasicTxDetailsExplorerIntentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        Intents.init()
        intending(hasAction(Intent.ACTION_VIEW))
            .respondWith(ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun viewOnExplorerOpensSoraMetricsUrl() {
        composeRule.setContent {
            SoraAppTheme {
                BasicTxDetails(
                    modifier = Modifier,
                    state = basicState(),
                    imageContent = {},
                    amountContent = {},
                    onCloseClick = {},
                    onCopy = { _ -> },
                    onOpenExplorer = { ShareUtil.shareInBrowser(composeRule.activity, it) },
                )
            }
        }

        composeRule
            .onNodeWithTag("$PACKAGE_ID:id/ViewOnExplorerButton")
            .performClick()

        intended(
            allOf(
                hasAction(Intent.ACTION_VIEW),
                hasData(Uri.parse(EXPLORER_URL)),
            )
        )
    }

    private fun basicState() = BasicTxDetailsState(
        txHash = "0xabc",
        blockHash = "0xblock",
        sender = "sender",
        infos = emptyList(),
        txStatus = TransactionStatus.COMMITTED,
        time = "26 May 2026 12:00",
        networkFee = null,
        networkFeeFiat = null,
        txTypeIcon = R.drawable.ic_arrow_down_24,
        txTypeTitle = "Transfer",
        explorerUrl = EXPLORER_URL,
    )

    private companion object {
        const val EXPLORER_URL = "https://sorametrics.org/sorav2?tab=extrinsics&q=0xabc"
    }
}
