package jp.co.soramitsu.feature_main_impl.presentation.detail

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.map
import jp.co.soramitsu.common.util.ext.plusAssign
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import java.math.BigDecimal

private const val SHORTEN_VOTES_AMOUNT = 1000

abstract class BaseDetailViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {
    protected val votesLiveData = MutableLiveData<BigDecimal>()
    val votesFormattedLiveData = votesLiveData.map(::formatVotes)

    init {
        startObservingVotes()
    }

    private fun startObservingVotes() {
        disposables += interactor.observeVotes()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWithDefaultError(votesLiveData::setValue)
    }

    fun votesClicked() {
        router.showVotesHistory()
    }

    fun backPressed() {
        router.popBackStack()
    }

    private fun formatVotes(votes: BigDecimal): String {
        val votesInt = votes.toInt()

        return if (votesInt > SHORTEN_VOTES_AMOUNT) {
            resourceManager.getString(R.string.project_votes_k_template).format(votesInt / SHORTEN_VOTES_AMOUNT)
        } else {
            numbersFormatter.formatInteger(votes)
        }
    }
}