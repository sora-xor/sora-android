/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.view

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

abstract class LoadMoreListener : RecyclerView.OnScrollListener() {

    abstract fun onLoadMore(elements: Int)

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
        val layoutManager = recyclerView.layoutManager
        if (dy > 0 && layoutManager is LinearLayoutManager) {
            val visibleElements = recyclerView.childCount
            val totalElements = layoutManager.itemCount
            val firstVisibleIndex = layoutManager.findFirstVisibleItemPosition()
            if (visibleElements + firstVisibleIndex >= totalElements) {
                onLoadMore(totalElements)
            }
        }
    }
}
