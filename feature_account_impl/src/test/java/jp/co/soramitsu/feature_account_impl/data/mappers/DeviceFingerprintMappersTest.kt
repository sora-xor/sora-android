package jp.co.soramitsu.feature_account_impl.data.mappers

import jp.co.soramitsu.feature_account_api.domain.model.DeviceFingerPrint
import jp.co.soramitsu.feature_account_impl.data.network.model.DeviceFingerPrintRemote
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class DeviceFingerprintMappersTest {

    @Test
    fun `map device fingerprint to to device fingerprint remote called`() {
        val deviceFingerPrintRemote = DeviceFingerPrintRemote(
            "message",
            "100",
            1000,
            2000,
            "ru",
            "ru",
            3
        )
        val deviceFingerPrint = DeviceFingerPrint(
            "message",
            "100",
            1000,
            2000,
            "ru",
            "ru",
            3
        )

        assertEquals(deviceFingerPrintRemote, mapDeviceFingerPrintToFingerPrintRemote(deviceFingerPrint))
    }
}