package jp.co.soramitsu.feature_assets_impl.presentation.scan

import android.app.Activity
import android.content.Intent
import com.google.zxing.client.android.Intents
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.util.ReleaseSurfacePolicy
import jp.co.soramitsu.feature_assets_api.presentation.QrScanPurpose
import jp.co.soramitsu.feature_assets_impl.presentation.screens.scan.QrScannerContractFactoryImpl
import jp.co.soramitsu.feature_assets_impl.presentation.screens.scan.shouldOpenScannedConnect
import org.junit.Assert.*
import org.junit.Test

class QrScannerPurposeTest {
    @Test fun `recipient scans never open Connect while general scans retain existing routing`() {
        for (scheme in listOf("iroha", "irohaconnect", "IROHA")) {
            assertFalse(shouldOpenScannedConnect(true, scheme, "connect", irohaConnectEnabled = true))
            assertTrue(shouldOpenScannedConnect(false, scheme, "connect", irohaConnectEnabled = true))
        }
        assertFalse(shouldOpenScannedConnect(false, "https", "connect", irohaConnectEnabled = true))
        assertFalse(shouldOpenScannedConnect(false, "iroha", "other", irohaConnectEnabled = true))
    }
    @Test fun `no scan opens Connect when IrohaConnect is off`() {
        for (scheme in listOf("iroha", "irohaconnect", "IROHA")) {
            assertFalse(shouldOpenScannedConnect(false, scheme, "connect", irohaConnectEnabled = false))
            assertFalse(shouldOpenScannedConnect(true, scheme, "connect", irohaConnectEnabled = false))
        }
    }
    @Test fun `scanner follows the release policy and only the store policy keeps IrohaConnect off in production`() {
        // SORA Nexus line: every flavor offers IrohaConnect.
        assertFalse(ReleaseSurfacePolicy.PRODUCTION_HIDES_SURFACES)
        assertTrue(ReleaseSurfacePolicy.irohaConnectAllowed(productionFlavor = true))
        assertTrue(ReleaseSurfacePolicy.irohaConnectAllowed(productionFlavor = false))
        // The 3.9.x store policy keeps it off in production only.
        assertFalse(
            ReleaseSurfacePolicy.irohaConnectAllowed(productionFlavor = true, productionHidesSurfaces = true),
        )
        assertTrue(
            ReleaseSurfacePolicy.irohaConnectAllowed(productionFlavor = false, productionHidesSurfaces = true),
        )
        assertTrue(ReleaseSurfacePolicy.irohaConnectAllowed)
        assertEquals(
            ReleaseSurfacePolicy.irohaConnectAllowed,
            shouldOpenScannedConnect(false, "iroha", "connect"),
        )
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
