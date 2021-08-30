package jp.co.soramitsu.common.presentation.view.chooserbottomsheet

import android.app.Activity
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.BottomSheetChooserBinding

class ChooserBottomSheet(
    context: Activity,
    title: Int,
    items: List<ChooserItem>,
    description: Int = 0,
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        val binding = BottomSheetChooserBinding.inflate(LayoutInflater.from(context), null, false)
            .also {
                setContentView(it.root)
            }

        binding.title.setText(title)
        if (description != 0)
            binding.info.setText(description)

        val adapter = ChooserAdapter(this)
        binding.recyclerView.adapter = adapter
        adapter.submitList(items)
    }
}
