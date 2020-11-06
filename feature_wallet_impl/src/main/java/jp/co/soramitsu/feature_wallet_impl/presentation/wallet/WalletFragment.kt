package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_ethereum_api.EthServiceStarter
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.asset.AssetAdapter
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.eth.EthAssetActionsBottomSheet
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events.LockBottomSheetBehavior
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events.RecentEventsAdapter
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.xor.ValAssetActionsBottomSheet
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.xor.ValBalanceBottomSheet
import kotlinx.android.synthetic.main.fragment_wallet.assetsRv
import kotlinx.android.synthetic.main.fragment_wallet.contentContainer
import kotlinx.android.synthetic.main.fragment_wallet.eventRecyclerView
import kotlinx.android.synthetic.main.fragment_wallet.moreView
import kotlinx.android.synthetic.main.fragment_wallet.pageContainer
import kotlinx.android.synthetic.main.fragment_wallet.placeholder
import kotlinx.android.synthetic.main.fragment_wallet.receiveTv
import kotlinx.android.synthetic.main.fragment_wallet.recentEventsBottomSheet
import kotlinx.android.synthetic.main.fragment_wallet.sendTv
import kotlinx.android.synthetic.main.fragment_wallet.swipeLayout
import javax.inject.Inject

class WalletFragment : BaseFragment<WalletViewModel>() {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    @Inject
    lateinit var ethServiceStarter: EthServiceStarter

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
            viewModel.refreshAssets()
            viewModel.updateTransactions()
        }

        if (bottomSheetExpanded) swipeLayout.isEnabled = false

        eventRecyclerView.layoutManager = LinearLayoutManager(activity!!)
        eventRecyclerView.adapter = RecentEventsAdapter(debounceClickHandler, itemListener)

        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                val totalItemCount = recyclerView.layoutManager?.itemCount
                val lastVisiblePosition = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                if (lastVisiblePosition + 1 == totalItemCount) {
                    viewModel.loadMoreEvents()
                }
            }
        }

        eventRecyclerView.addOnScrollListener(scrollListener)

        moreView.setOnClickListener {
            viewModel.assetSettingsClicked()
        }
    }

    override fun onStart() {
        super.onStart()
        pageContainer.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    override fun onStop() {
        super.onStop()
        pageContainer.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
    }

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        bottomSheetBehavior.peekHeight = pageContainer.measuredHeight - contentContainer.measuredHeight
    }

    override fun subscribe(viewModel: WalletViewModel) {
        observe(viewModel.assetsLiveData, Observer {
            if (assetsRv.adapter == null) {
                assetsRv.layoutManager = LinearLayoutManager(activity!!)
                assetsRv.adapter = AssetAdapter { viewModel.assetClicked(it) }
            }
            (assetsRv.adapter as AssetAdapter).submitList(it)
        })

        observe(viewModel.transactionsModelLiveData, Observer { transactions ->
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

        observe(viewModel.showEthBottomSheetEvent, EventObserver {
            openEthAddressInfo(it, viewModel)
        })

        observe(viewModel.showXorAddressBottomSheetEvent, EventObserver {
            openXorAddressesInfo()
        })

        observe(viewModel.showXorBalancesBottomSheetEvent, EventObserver {
            openXorBalancesInfo(it)
        })

        observe(viewModel.copiedAddressEvent, EventObserver {
            Toast.makeText(activity!!, R.string.common_copied, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.retryEthRegisterEvent, EventObserver {
            ethServiceStarter.startForRetry()
        })

        viewModel.refreshAssets()
        viewModel.updateTransactions()
    }

    private fun openXorBalancesInfo(balances: ValBalances) {
        val bottomSheet = ValBalanceBottomSheet(requireActivity(), balances.sora, balances.eth)

        bottomSheet.show()
    }

    private fun openXorAddressesInfo() {
        val bottomSheet = ValAssetActionsBottomSheet(requireActivity(), { assetId ->
            viewModel.copyAssetAddressClicked(assetId)
        }, {
            viewModel.viewXorBalanceClicked()
        })

        bottomSheet.show()
    }

    private fun openEthAddressInfo(params: Pair<String, Boolean>, viewModel: WalletViewModel) {
        val bottomSheet = EthAssetActionsBottomSheet(requireActivity(), params.first, params.second, {
            viewModel.copyEthClicked()
        }, {
            viewModel.retryEthRegisterClicked()
        })

        bottomSheet.show()
    }
}