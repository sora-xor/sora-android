/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.bottomsheet.EventItemDecoration
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.bottomsheet.LockBottomSheetBehavior
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.bottomsheet.RecentEventsAdapter
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction
import kotlinx.android.synthetic.main.fragment_wallet.balanceTv
import kotlinx.android.synthetic.main.fragment_wallet.contentContainer
import kotlinx.android.synthetic.main.fragment_wallet.currencyTv
import kotlinx.android.synthetic.main.fragment_wallet.eventRecyclerView
import kotlinx.android.synthetic.main.fragment_wallet.howItWorksCard
import kotlinx.android.synthetic.main.fragment_wallet.pageContainer
import kotlinx.android.synthetic.main.fragment_wallet.placeholder
import kotlinx.android.synthetic.main.fragment_wallet.receiveTv
import kotlinx.android.synthetic.main.fragment_wallet.recentEventsBottomSheet
import kotlinx.android.synthetic.main.fragment_wallet.sendTv
import kotlinx.android.synthetic.main.fragment_wallet.swipeLayout
import javax.inject.Inject

class WalletFragment : BaseFragment<WalletViewModel>() {

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var bottomSheetBehavior: LockBottomSheetBehavior<View>

    private var bottomSheetExpanded = false

    private val itemListener: (SoraTransaction) -> Unit = {
        viewModel.eventClicked(it)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_wallet, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(context!!, WalletFeatureApi::class.java)
            .walletSubComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as BottomBarController).showBottomBar()

        currencyTv.text = Const.SORA_SYMBOL

        sendTv.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                viewModel.sendButtonClicked()
            }
        )

        receiveTv.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                viewModel.receiveButtonClicked()
            }
        )

        howItWorksCard.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                viewModel.btnHelpClicked()
            }
        )

        bottomSheetBehavior = LockBottomSheetBehavior.fromView(recentEventsBottomSheet)
        bottomSheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                bottomSheetExpanded = BottomSheetBehavior.STATE_EXPANDED == newState
                if (isAdded) swipeLayout.isEnabled = !bottomSheetExpanded
            }
        })

        swipeLayout.setOnRefreshListener {
            viewModel.getBalance(true)
            viewModel.getTransactionHistory(true)
        }

        pageContainer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                pageContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                bottomSheetBehavior.peekHeight = pageContainer.measuredHeight - contentContainer.measuredHeight
            }
        })

        if (bottomSheetExpanded) swipeLayout.isEnabled = false

        eventRecyclerView.layoutManager = LinearLayoutManager(activity!!)
        eventRecyclerView.adapter = RecentEventsAdapter(debounceClickHandler, itemListener)
        eventRecyclerView.addItemDecoration(EventItemDecoration(context!!))
    }

    override fun subscribe(viewModel: WalletViewModel) {
        observe(viewModel.balanceLiveData, Observer {
            balanceTv.text = it
        })

        observe(viewModel.transactionsLiveData, Observer { transactions ->
            (eventRecyclerView.adapter as RecentEventsAdapter).submitList(transactions)
            if (transactions.isEmpty()) {
                placeholder.show()
                eventRecyclerView.gone()
                bottomSheetBehavior.isDraggable = false
            } else {
                placeholder.gone()
                eventRecyclerView.show()
                bottomSheetBehavior.isDraggable = true
            }
        })

        observe(viewModel.hideSwipeProgressLiveData, Observer {
            swipeLayout.isRefreshing = false
        })

        viewModel.getBalance(false)
        viewModel.getTransactionHistory(false)
    }
}