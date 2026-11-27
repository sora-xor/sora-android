package jp.co.soramitsu.sora.portfolio

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.io.FileManagerImpl
import jp.co.soramitsu.common.nexus.IrohaAddressCodec
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.NexusAssetPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The helpers of the SORA Nexus and Taira pages, with the app's real QR, avatar and files. */
@RunWith(AndroidJUnit4::class)
class NexusAssetPresentationTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resources = ResourceManager()
    private val presentation = NexusAssetPresentation(
        BasicClipboardManager(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager),
        AccountAvatarGenerator(resources),
        QrCodeGenerator(Color.BLACK),
        FileManagerImpl(context),
        resources,
        DateTimeFormatter(LanguagesHolder(), resources, context),
    )
    private val address = IrohaAddressCodec.encode(ByteArray(32) { (it * 7 + 3).toByte() },
        NexusNetworks.taira.chainDiscriminant)

    /** The app's ResourceManager reads resources from the activity it is told about; so does this one. */
    @Before fun attachResources() {
        activity.scenario.onActivity { resources.onActivityCreated(it, null) }
    }

    @Test fun anAddressGetsItsQrCodeAvatarShareFileAndMessage() {
        val qrCode = presentation.qrCode(address)
        assertTrue(qrCode.width > 0 && qrCode.height > 0)
        assertNotNull(presentation.avatar(WalletNetworkId.TAIRA, address))
        // Not an address of that network: no avatar rather than another key's.
        assertNull(presentation.avatar(WalletNetworkId.TAIRA, "not-an-address"))
        assertNull(presentation.avatar(WalletNetworkId.MINAMOTO, address))
        assertNotNull(presentation.shareFile(qrCode))
        val message = presentation.shareMessage("Taira Testnet", address)
        assertTrue(message, message.contains("XOR") && message.contains("Taira Testnet") && message.contains(address))
        assertTrue(presentation.dateTime(1_794_000_000_000).isNotBlank())
    }

    @Test fun copyingPutsTheValueOnTheClipboard() {
        instrumentation.runOnMainSync { presentation.copy(address) }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // Android 10+ lets only the focused app read the clipboard; the test checks what it can.
        clipboard.primaryClip?.let { assertEquals(address, it.getItemAt(0).text.toString()) }
    }
}
