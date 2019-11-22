/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.wallet

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.core_ui.presentation.list.BaseListAdapter
import jp.co.soramitsu.core_ui.presentation.list.Section
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.wallet.mappers.EventsConverter
import jp.co.soramitsu.operationbutton.view.OperationButton
import jp.co.soramitsu.operationbutton.view.OperationParameters
import jp.co.soramitsu.recent_events.list.EventItemDecoration
import jp.co.soramitsu.recent_events.list.EventSection
import jp.co.soramitsu.recent_events.list.models.EventHeader
import jp.co.soramitsu.recent_events.list.models.EventItem
import jp.co.soramitsu.recent_events.list.view.LockBottomSheetBehavior
import kotlinx.android.synthetic.main.fragment_wallet.howItWorksCard
import kotlinx.android.synthetic.main.fragment_wallet.pageContainer
import kotlinx.android.synthetic.main.fragment_wallet.recent_events
import kotlinx.android.synthetic.main.fragment_wallet.swipeLayout
import javax.inject.Inject

@SuppressLint("CheckResult")
class WalletFragment : BaseFragment<WalletViewModel>() {

    private lateinit var doubleButtonLeftButton: TextView
    private lateinit var doubleButtonRightButton: TextView
    private lateinit var accountInformationCardCurrencyTextView: TextView
    private lateinit var accountInformationCardBodyTextView: TextView
    private lateinit var accountInformationCardHeaderTextView: TextView
    private lateinit var accountInformationCardRightBodyTextView: TextView
    private lateinit var accountInformationCardCurrencyContainer: ConstraintLayout
    private lateinit var bottomSheetBehavior: LockBottomSheetBehavior<View>
    private lateinit var contentContainer: ConstraintLayout
    private lateinit var recyclerView: RecyclerView

    private lateinit var sectionAdapter: BaseListAdapter

    private var bottomSheetExpanded = false

    @Inject lateinit var numbersFormatter: NumbersFormatter

    private val itemListener: (EventItem) -> Unit = {
        viewModel.eventClicked(it)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_wallet, container, false)

        accountInformationCardCurrencyContainer = view.findViewById(R.id.accountInformationCardCurrencyContainer)
        accountInformationCardCurrencyTextView = view.findViewById(R.id.accountInformationCardCurrencyTextView)
        accountInformationCardBodyTextView = view.findViewById(R.id.accountInformationCardBodyTextView)
        accountInformationCardHeaderTextView = view.findViewById(R.id.accountInformationCardHeaderTextView)
        accountInformationCardRightBodyTextView = view.findViewById(R.id.accountInformationCardRightBodyTextView)
        contentContainer = view.findViewById(R.id.contentContainer)
        doubleButtonLeftButton = view.findViewById(R.id.doubleButtonLeftButton)
        doubleButtonRightButton = view.findViewById(R.id.doubleButtonRightButton)

        sectionAdapter = BaseListAdapter()

        recyclerView = view.findViewById(R.id.eventRecyclerView)
        recyclerView.adapter = sectionAdapter
        recyclerView.layoutManager = LinearLayoutManager(activity)

        val operationButton = view.findViewById<OperationButton>(R.id.operationButton)
        operationButton?.setParams(
            OperationParameters
                .builder()
                .operationLogo(R.drawable.icon_wallet_reward)
                .operationTitle(getString(R.string.next))
                .build()
        )

        val filterButton = view.findViewById<ImageView>(R.id.eventFilterImageView)
        filterButton.gone()

        return view
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .walletSubComponentBuilder()
            .withFragment(this)
            .withRouter(activity as MainRouter)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as MainActivity).showBottomView()

        accountInformationCardCurrencyTextView.text = Const.SORA_SYMBOL
        accountInformationCardBodyTextView.text = getString(R.string.xor)
        accountInformationCardHeaderTextView.text = ""
        accountInformationCardRightBodyTextView.gone()

        val background = accountInformationCardCurrencyContainer.background

        val drawable = background as LayerDrawable
        val shape = drawable.findDrawableByLayerId(R.id.background) as GradientDrawable
        shape.setColor(ContextCompat.getColor(this.context!!, R.color.lightRed))

        doubleButtonLeftButton.text = getString(R.string.send)
        doubleButtonRightButton.text = getString(R.string.receive)

        doubleButtonLeftButton.setOnClickListener { viewModel.sendButtonClicked() }

        doubleButtonRightButton.setOnClickListener { viewModel.receiveButtonClicked() }

        howItWorksCard.setOnClickListener { viewModel.btnHelpClicked() }

        bottomSheetBehavior = LockBottomSheetBehavior.fromView(recent_events)
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
    }

    override fun subscribe(viewModel: WalletViewModel) {
        observe(viewModel.balanceLiveData, Observer {
            accountInformationCardHeaderTextView.text = it
        })

        observe(viewModel.transactionsLiveData, Observer { transactions ->
            sectionAdapter.removeAllSections()
            if (transactions.isEmpty()) {
                val section = EventSection(sectionAdapter.getAsyncDiffer(EventItem.diffCallback) as AsyncListDiffer<EventItem>)
                sectionAdapter.addSection(section as Section<Nothing, Nothing, Nothing>)
                sectionAdapter.notifyDataSetChanged()
                section.state = Section.State.EMPTY
                bottomSheetBehavior.isDraggable = false
            } else {
                for (item in EventsConverter.fromTransactionVmToCard(transactions, context!!, numbersFormatter)) {
                    val section = EventSection(sectionAdapter.getAsyncDiffer(EventItem.diffCallback) as AsyncListDiffer<EventItem>)
                    section.addHeaderItem(EventHeader(item.key))
                    section.submitContentItems(item.value, itemListener)
                    sectionAdapter.addSection(section as Section<Nothing, Nothing, Nothing>)
                }

                sectionAdapter.notifyDataSetChanged()
                recyclerView.addItemDecoration(EventItemDecoration(context!!))
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