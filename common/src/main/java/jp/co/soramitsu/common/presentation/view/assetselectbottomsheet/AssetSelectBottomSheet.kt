package jp.co.soramitsu.common.presentation.view.assetselectbottomsheet

import android.view.LayoutInflater
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.BottomSheetAssetListBinding
import jp.co.soramitsu.common.presentation.view.assetselectbottomsheet.adapter.AssetListAdapter
import jp.co.soramitsu.common.presentation.view.assetselectbottomsheet.adapter.AssetListItemModel
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import java.util.Locale

class AssetSelectBottomSheet(
    fragment: Fragment,
    private val items: List<AssetListItemModel>,
    private val dismissListener: () -> Unit,
    private val itemClickedListener: (AssetListItemModel) -> Unit
) : BottomSheetDialog(fragment.requireContext(), R.style.BottomSheetDialog) {

    private var curFilter: String = ""
    private val adapter by lazy { AssetListAdapter({ itemClickedListener(it); dismiss() }, true) }

    private val queryListener = object : SearchView.OnQueryTextListener {

        override fun onQueryTextSubmit(query: String?): Boolean {
            fragment.hideSoftKeyboard(fragment.requireActivity())
            searchAssets(query.orEmpty())
            return true
        }

        override fun onQueryTextChange(newText: String?): Boolean {
            searchAssets(newText.orEmpty())
            return true
        }
    }

    init {
        val binding = BottomSheetAssetListBinding.inflate(LayoutInflater.from(context), null, false)
            .also {
                setContentView(it.root)
            }

        binding.svAssetList.setOnQueryTextListener(queryListener)

        binding.rvAssetList.adapter = adapter
        adapter.submitList(items)

        setOnDismissListener {
            dismissListener()
        }
    }

    fun searchAssets(filter: String) {
        curFilter = filter
        filterAssetsList()
    }

    private fun filterAssetsList() {
        val filter = curFilter.lowercase(Locale.getDefault())
        val newList = if (curFilter.isBlank()) items
        else mutableListOf<AssetListItemModel>().apply {
            addAll(
                items.filter {
                    it.title.lowercase(Locale.getDefault())
                        .contains(filter) || it.tokenName.lowercase(Locale.getDefault())
                        .contains(filter)
                }
            )
        }

        adapter.submitList(newList)
        adapter.notifyDataSetChanged()
    }
}
