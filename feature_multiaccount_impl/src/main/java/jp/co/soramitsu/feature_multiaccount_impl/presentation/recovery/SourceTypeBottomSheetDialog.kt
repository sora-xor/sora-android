/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.recovery

import android.app.Activity
import jp.co.soramitsu.common.presentation.view.chooserbottomsheet.ChooserBottomSheet
import jp.co.soramitsu.common.presentation.view.chooserbottomsheet.ChooserItem
import jp.co.soramitsu.feature_multiaccount_impl.R

class SourceTypeBottomSheetDialog(
    context: Activity,
    sourceTypeSelected: (SourceType) -> Unit,
    selectedSourceType: SourceType
) : ChooserBottomSheet(
    context,
    R.string.recovery_source_type,
    listOf(
        ChooserItem(
            title = R.string.common_passphrase_title,
            clickHandler = { sourceTypeSelected(SourceType.PASSPHRASE) },
            selected = selectedSourceType == SourceType.PASSPHRASE
        ),
        ChooserItem(
            title = R.string.common_raw_seed,
            clickHandler = { sourceTypeSelected(SourceType.RAW_SEED) },
            selected = selectedSourceType == SourceType.RAW_SEED
        )
    )
)

enum class SourceType {
    PASSPHRASE,
    RAW_SEED,
    JSON
}
