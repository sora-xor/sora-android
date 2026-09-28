package jp.co.soramitsu.feature_assets_impl.presentation.screens.scan

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.journeyapps.barcodescanner.ScanOptions
import javax.inject.Inject
import jp.co.soramitsu.feature_assets_api.presentation.QrScanPurpose
import jp.co.soramitsu.feature_assets_api.presentation.QrScannerContractFactory

internal const val RECIPIENT_SCAN_EXTRA = "jp.co.soramitsu.sora.scan.RECIPIENT_ONLY"

class QrScannerContractFactoryImpl @Inject constructor() : QrScannerContractFactory {
    override fun create(purpose: QrScanPurpose): ActivityResultContract<Unit, String?> =
        object : ActivityResultContract<Unit, String?>() {
            override fun createIntent(context: Context, input: Unit): Intent = ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("")
                .setBeepEnabled(false)
                .setCaptureActivity(QRCodeScannerActivity::class.java)
                .createScanIntent(context)
                .putExtra(RECIPIENT_SCAN_EXTRA, purpose == QrScanPurpose.RECIPIENT)

            override fun parseResult(resultCode: Int, intent: Intent?): String? =
                ScanTextContract().parseResult(resultCode, intent)
        }
}

/** Recipient mode never navigates to a scanned application's connect request. */
internal fun shouldOpenScannedConnect(recipientOnly: Boolean, scheme: String?, host: String?): Boolean =
    !recipientOnly && scheme?.lowercase() in setOf("iroha", "irohaconnect") && host == "connect"
