package jp.co.soramitsu.feature_assets_impl.presentation.scan

import android.app.Activity
import android.content.Intent
import com.google.zxing.client.android.Intents
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.feature_assets_api.presentation.QrScanPurpose
import jp.co.soramitsu.feature_assets_impl.presentation.screens.scan.QrScannerContractFactoryImpl
import jp.co.soramitsu.feature_assets_impl.presentation.screens.scan.shouldOpenScannedConnect
import org.junit.Assert.*
import org.junit.Test

class QrScannerPurposeTest {
    @Test fun `recipient scans never open Connect while general scans retain existing routing`() {
        for (scheme in listOf("iroha", "irohaconnect", "IROHA")) {
            assertFalse(shouldOpenScannedConnect(true, scheme, "connect"))
            assertTrue(shouldOpenScannedConnect(false, scheme, "connect"))
        }
        assertFalse(shouldOpenScannedConnect(false, "https", "connect"))
        assertFalse(shouldOpenScannedConnect(false, "iroha", "other"))
    }
    @Test fun `camera and gallery text result is preserved while cancellation returns nothing`() {
        val data = mockk<Intent>()
        every { data.getStringExtra(Intents.Scan.RESULT) } returns "iroha://connect?test=untrusted"
        for (purpose in QrScanPurpose.entries) {
            val contract = QrScannerContractFactoryImpl().create(purpose)
            assertEquals("iroha://connect?test=untrusted", contract.parseResult(Activity.RESULT_OK, data))
            assertNull(contract.parseResult(Activity.RESULT_CANCELED, data))
            assertNull(contract.parseResult(Activity.RESULT_OK, null))
        }
    }
}
