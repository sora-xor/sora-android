package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.hide

import android.app.Activity
import android.view.LayoutInflater
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.AssetSettingsViewModel
import kotlinx.android.synthetic.main.bottom_sheet_hiding_assets.addTv
import kotlinx.android.synthetic.main.bottom_sheet_hiding_assets.cancelTv
import kotlinx.android.synthetic.main.bottom_sheet_hiding_assets.hidingAssetsRv

class HidingAssetsBottomSheet(
    context: Activity,
    private val lifecycleOwner: LifecycleOwner,
    private val assetSettingsViewModel: AssetSettingsViewModel
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.bottom_sheet_hiding_assets, null))

        addTv.setOnClickListener {
            assetSettingsViewModel.displayAssetsButtonClicked()
            dismiss()
        }

        cancelTv.setOnClickListener {
            dismiss()
        }

        assetSettingsViewModel.hidingAssetsLiveData.observe(lifecycleOwner, Observer {
            if (hidingAssetsRv.adapter == null) {
                hidingAssetsRv.layoutManager = LinearLayoutManager(context)
                hidingAssetsRv.setHasFixedSize(true)
                hidingAssetsRv.adapter = AssetHidingAdapter { asset, checked ->
                    assetSettingsViewModel.hidingAssetCheckChanged(asset, checked)
                }
            }
            (hidingAssetsRv.adapter as AssetHidingAdapter).submitList(it)
        })

        assetSettingsViewModel.addButtonEnabledLiveData.observe(lifecycleOwner, Observer { enabled ->
            addTv.isEnabled = enabled
        })
    }
}