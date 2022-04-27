/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import jp.co.soramitsu.common.presentation.view.table.RowsView
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.ViewDetailsSectionBinding
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.model.DetailsItem

class DetailsSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private val binding = ViewDetailsSectionBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = VERTICAL
    }

    fun setData(title: String, items: List<DetailsItem>) {
        binding.sectionItems.inflateRows(RowsView.RowType.LINE, items.size)
        binding.sectionItems.run {
            items.forEachIndexed { index, item ->
                updateValuesInRow(
                    index,
                    item.title,
                    item.value,
                    image = item.messageAlert?.let { R.drawable.ic_neu_exclamation }
                )

                if (item.messageAlert != null) {
                    setOnClickListener(index) {
                        AlertDialog.Builder(context)
                            .setTitle(item.messageAlert.title)
                            .setMessage(item.messageAlert.message)
                            .setPositiveButton(item.messageAlert.positiveButton) { _, _ -> }
                            .show()
                    }
                }
            }
        }

        binding.sectionTitle.text = title
        binding.sectionTitle.showOrGone(items.isNotEmpty())
    }
}
