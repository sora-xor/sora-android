package jp.co.soramitsu.feature_main_impl.presentation.voteshistory

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.domain.TimeSectionInteractor
import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.model.VotesHistoryItem

class VotesHistoryViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val preloader: WithPreloader,
    private val timeSectionInteractor: TimeSectionInteractor
) : BaseViewModel(), WithPreloader by preloader {

    companion object {
        private const val VOTES_HISTORY_PER_PAGE = 50
    }

    val votesHistoryLiveData = MutableLiveData<List<VotesHistoryItem>>()
    val showEmptyLiveData = MutableLiveData<Boolean>()

    private var votesHistoryOffset = 0
    private var loading = false
    private var lastPageLoaded = false

    fun loadHistory(updateCached: Boolean) {
        votesHistoryOffset = 0
        lastPageLoaded = false
        if (votesHistoryLiveData.value == null) showPreloader()
    }

    fun loadMoreHistory() {
        if (loading || lastPageLoaded) return
    }

    fun backButtonClick() {
        router.popBackStack()
    }
}
