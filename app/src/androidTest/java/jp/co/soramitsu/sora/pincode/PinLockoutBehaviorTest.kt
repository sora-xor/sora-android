@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.pincode

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.*
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_main_impl.presentation.pincode.PinCodeViewModel
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Uses Android's real CountDownTimer and main looper with near-expiry synthetic timestamps. */
@RunWith(AndroidJUnit4::class)
class PinLockoutBehaviorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(check: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 6000
        var ready = false
        while (!ready && android.os.SystemClock.uptimeMillis() < deadline) {
            main { ready = check() }
            if (!ready) android.os.SystemClock.sleep(25)
        }
        assertTrue("PIN timer transition timed out", ready)
    }
    private fun model(interactor: PinCodeInteractor): PinCodeViewModel {
        val resource = mockk<ResourceManager> {
            every { getString(any()) } answers { instrumentation.targetContext.getString(firstArg()) }
            every { getString(R.string.pin_locked_for_title, any()) } returns "Locked"
        }
        val connection = mockk<ConnectionManager> { every { connectionState } returns MutableStateFlow(true) }
        return PinCodeViewModel(interactor, mockk<MainInteractor>(relaxed = true), mockk<MainRouter>(relaxed = true), resource,
            mockk<SelectNodeRouter>(relaxed = true), mockk<WithProgress>(relaxed = true), mockk<DeviceVibrator>(relaxed = true), connection)
    }
    @Test fun eachPersistedLockoutDurationBlocksDigitsUntilItsRealTimerExpires() {
        for ((attempts, minutes) in listOf(3 to 1, 4 to 5, 5 to 15, 6 to 30)) {
            val interactor = mockk<PinCodeInteractor>(relaxed = true) {
                coEvery { retrieveTriesUsed() } returns attempts
                coEvery { retrieveTimerStartedTimestamp() } answers { System.currentTimeMillis() - minutes * 60000L + 1200L }
                coEvery { isBiometryEnabled() } returns true
            }
            val store = ViewModelStore(); lateinit var vm: PinCodeViewModel
            main { vm = model(interactor); store.put("pin", vm) }
            try {
                await { vm.state.toolbarTitleString == "Locked" }
                main { vm.pinCodeNumberClicked("1"); assertEquals(0, vm.state.checkedDotsCount) }
                await { vm.state.toolbarTitleString == instrumentation.targetContext.getString(R.string.pincode_enter_pin_code) }
                main { vm.pinCodeNumberClicked("1"); assertEquals(1, vm.state.checkedDotsCount); assertEquals(true, vm.showFingerPrintEventLiveData.value) }
                coVerify(atLeast = 1) { interactor.resetTimerStartedTimestamp() }
            } finally { main { store.clear() } }
        }
    }
    @Test fun expiredTimestampAtAnUnrestrictedAttemptCountRestoresInputImmediately() {
        val interactor = mockk<PinCodeInteractor>(relaxed = true) {
            coEvery { retrieveTriesUsed() } returns 2
            coEvery { retrieveTimerStartedTimestamp() } returns 1L
        }
        val store = ViewModelStore(); lateinit var vm: PinCodeViewModel
        main { vm = model(interactor); store.put("pin", vm) }
        try {
            await { vm.state.toolbarTitleString == instrumentation.targetContext.getString(R.string.pincode_enter_pin_code) }
            main { vm.pinCodeNumberClicked("1"); assertEquals(1, vm.state.checkedDotsCount) }
            coVerify(atLeast = 1) { interactor.resetTimerStartedTimestamp() }
        } finally { main { store.clear() } }
    }
}
