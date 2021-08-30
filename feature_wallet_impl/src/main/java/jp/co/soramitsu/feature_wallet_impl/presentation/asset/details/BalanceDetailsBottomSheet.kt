package jp.co.soramitsu.feature_wallet_impl.presentation.asset.details

import android.app.Activity
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.BottomSheetBalanceDetailsBinding
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.details.model.FrozenXorDetailsModel

class BalanceDetailsBottomSheet(
    context: Activity,
    frozenXorDetailsModel: FrozenXorDetailsModel
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        val binding =
            BottomSheetBalanceDetailsBinding.inflate(LayoutInflater.from(context), null, false)
                .also {
                    setContentView(it.root)
                }

        binding.tvBondedValue.text = frozenXorDetailsModel.bonded
        binding.tvFrozenValue.text = frozenXorDetailsModel.frozen
        binding.tvLockedValue.text = frozenXorDetailsModel.locked
        binding.tvRedeemableValue.text = frozenXorDetailsModel.redeemable
        binding.tvReservedValue.text = frozenXorDetailsModel.reserved
        binding.tvUnbondingValue.text = frozenXorDetailsModel.unbonding
    }
}
