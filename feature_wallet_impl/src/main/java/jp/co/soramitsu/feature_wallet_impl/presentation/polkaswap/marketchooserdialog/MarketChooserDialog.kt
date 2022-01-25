/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.marketchooserdialog

import android.app.Activity
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_impl.databinding.BottomSheetMarketChooserBinding

class MarketChooserDialog(
    context: Activity,
    clickHandler: (Market) -> Unit,
    markets: List<Market>,
    selectedMarket: Market
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        val binding = BottomSheetMarketChooserBinding.inflate(LayoutInflater.from(context), null, false)
            .also {
                setContentView(it.root)
            }

        binding.info.setText(selectedMarket.descriptionResource)

        when (selectedMarket) {
            Market.SMART -> selectSmart(binding)
            Market.TBC -> selectTbc(binding)
            Market.XYK -> selectXyk(binding)
        }

        binding.tbcWrapper.showOrGone(markets.contains(Market.TBC))
        binding.xykWrapper.showOrGone(markets.contains(Market.XYK))

        binding.xykWrapper.setOnClickListener {
            clickHandler(Market.XYK)
            selectXyk(binding)
            dismiss()
        }

        binding.tbcWrapper.setOnClickListener {
            clickHandler(Market.TBC)
            selectTbc(binding)
            dismiss()
        }

        binding.smartWrapper.setOnClickListener {
            clickHandler(Market.SMART)
            selectSmart(binding)
            dismiss()
        }
    }

    private fun selectXyk(binding: BottomSheetMarketChooserBinding) {
        binding.itemSmart.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        binding.itemXyk.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_checkmark_green, 0)
        binding.itemTbc.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
    }

    private fun selectSmart(binding: BottomSheetMarketChooserBinding) {
        binding.itemSmart.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_checkmark_green, 0)
        binding.itemXyk.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        binding.itemTbc.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
    }

    private fun selectTbc(binding: BottomSheetMarketChooserBinding) {
        binding.itemSmart.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        binding.itemXyk.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        binding.itemTbc.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_checkmark_green, 0)
    }
}
