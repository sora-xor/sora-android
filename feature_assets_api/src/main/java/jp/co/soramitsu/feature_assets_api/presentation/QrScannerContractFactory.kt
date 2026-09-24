package jp.co.soramitsu.feature_assets_api.presentation

import androidx.activity.result.contract.ActivityResultContract

enum class QrScanPurpose { GENERAL, RECIPIENT }

/** Only decoded text crosses the feature boundary; the caller validates its purpose. */
interface QrScannerContractFactory {
    fun create(purpose: QrScanPurpose): ActivityResultContract<Unit, String?>
}
