/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode.fingerprint

import android.app.KeyguardManager
import androidx.core.hardware.fingerprint.FingerprintManagerCompat
import androidx.core.os.CancellationSignal

class FingerprintWrapper(
    private val manager: FingerprintManagerCompat,
    keyguardManager: KeyguardManager,
    private val fingerPrintListener: FingerPrintListener
) : FingerprintManagerCompat.AuthenticationCallback() {

    companion object {
        private const val CANCEL_SCANNING_MESSAGE_ID = 5
    }

    private var cancellationSignal: CancellationSignal? = null

    private val sensorState: SensorState = getSensorState(keyguardManager)

    enum class SensorState {
        NOT_SUPPORTED,
        NOT_BLOCKED,
        NO_FINGERPRINTS,
        READY
    }

    fun startAuth() {
        if (isSensorReady()) {
            cancellationSignal = CancellationSignal()
            manager.authenticate(null, 0, cancellationSignal, this, null)
            fingerPrintListener.showFingerPrintDialog()
        }
    }

    fun cancel() {
        fingerPrintListener.hideFingerPrintDialog()
        cancellationSignal?.cancel()
    }

    fun isSensorReady(): Boolean {
        return SensorState.READY == sensorState
    }

    override fun onAuthenticationError(errMsgId: Int, errString: CharSequence) {
        if (errMsgId != CANCEL_SCANNING_MESSAGE_ID) {
            fingerPrintListener.onAuthenticationError(errString.toString())
        }
    }

    override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence) {
        fingerPrintListener.onAuthenticationHelp(helpString.toString())
    }

    override fun onAuthenticationSucceeded(result: FingerprintManagerCompat.AuthenticationResult) {
        fingerPrintListener.hideFingerPrintDialog()
        fingerPrintListener.onFingerPrintSuccess()
    }

    override fun onAuthenticationFailed() {
        fingerPrintListener.onAuthFailed()
    }

    private fun getSensorState(keyguardManager: KeyguardManager): SensorState {
        if (manager.isHardwareDetected) {
            if (!keyguardManager.isKeyguardSecure) {
                return SensorState.NOT_BLOCKED
            }
            return if (manager.hasEnrolledFingerprints()) {
                SensorState.READY
            } else {
                SensorState.NO_FINGERPRINTS
            }
        } else {
            return SensorState.NOT_SUPPORTED
        }
    }

    fun toggleScanner() {
        if (cancellationSignal != null && cancellationSignal!!.isCanceled) {
            startAuth()
        } else {
            cancel()
        }
    }
}