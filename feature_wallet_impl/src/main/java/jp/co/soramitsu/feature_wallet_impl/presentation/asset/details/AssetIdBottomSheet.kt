package jp.co.soramitsu.feature_wallet_impl.presentation.asset.details

import android.app.Activity
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.BottomSheetAssetIdBinding

class AssetIdBottomSheet(
    context: Activity,
    assetId: String,
    private val copyClickListener: () -> Unit,
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        val binding =
            BottomSheetAssetIdBinding.inflate(LayoutInflater.from(context), null, false)
                .also {
                    setContentView(it.root)
                }

        binding.assetIdValue.text = assetId.truncateUserAddress()
        binding.assetIdValue.setOnClickListener {
            copyClickListener()
        }
    }
}
