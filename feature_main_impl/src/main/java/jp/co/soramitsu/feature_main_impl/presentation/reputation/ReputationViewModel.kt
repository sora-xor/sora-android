package jp.co.soramitsu.feature_main_impl.presentation.reputation

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.TimerWrapper
import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_information_api.domain.model.InformationContainer
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import java.math.BigDecimal
import java.util.Calendar
import java.util.TimeZone

class ReputationViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val timer: TimerWrapper,
    private val resourceManager: ResourceManager,
    private val numbersFormatter: NumbersFormatter
) : BaseViewModel() {

    companion object {
        private const val ASIA_TIMEZONE = "Asia/Tokyo"
        private const val DISTRIBUTION_HOUR = 13
        private const val DISTRIBUTION_MINUTE = 37
        private const val DISTRIBUTION_SECOND = 0
        private const val FULL_DAY_MILLIS = 24 * 60 * 60 * 1000
    }

    val reputationLiveData = MutableLiveData<Reputation>()
    val calculatingReputationLiveData = MutableLiveData<String>()
    val lastVotesLiveData = MutableLiveData<String>()
    val reputationContentLiveData = MutableLiveData<List<InformationContainer>>()

    fun loadReputation(updateCached: Boolean) {
        disposables.add(
            interactor.getReputationWithLastVotes(updateCached)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val reputation = it.first
                    if (reputation.rank <= 0) {
                        setupTimer()
                    } else {
                        timer.cancel()
                        reputationLiveData.value = reputation
                        val lastVotes = it.second
                        lastVotesLiveData.value = if (lastVotes > BigDecimal.ZERO) {
                            numbersFormatter.formatInteger(lastVotes)
                        } else {
                            ""
                        }
                    }
                    if (!updateCached) loadReputation(true)
                }, {
                    logException(it)
                })
        )
    }

    fun backButtonClick() {
        router.popBackStack()
    }

    fun loadInformation(updateCached: Boolean) {
        disposables.add(
            interactor.getReputationContent(updateCached)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (!updateCached) loadInformation(true)
                    reputationContentLiveData.value = it
                }, {
                    logException(it)
                })
        )
    }

    private fun setupTimer() {
        if (timer.isStarted()) return

        val schedulerCalendar = Calendar.getInstance(TimeZone.getTimeZone(ASIA_TIMEZONE)).apply {
            set(Calendar.HOUR_OF_DAY, DISTRIBUTION_HOUR)
            set(Calendar.MINUTE, DISTRIBUTION_MINUTE)
            set(Calendar.SECOND, DISTRIBUTION_SECOND)
        }

        val currentCalendar = Calendar.getInstance()

        val diffInMillis = calcDiffInMillis(currentCalendar.timeInMillis, schedulerCalendar.timeInMillis)
        disposables.add(
            timer.start(diffInMillis)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val timeLeftStr = resourceManager.getString(R.string.reputation_timer_format).format(timer.calcTimeLeft(it))
                    calculatingReputationLiveData.value = timeLeftStr
                }, {
                    it.printStackTrace()
                }, {
                    calculatingReputationLiveData.value = resourceManager.getString(R.string.reputation_soon)
                })
        )
    }

    private fun calcDiffInMillis(currentTime: Long, schedulerTime: Long): Long {
        return if (schedulerTime > currentTime) {
            schedulerTime - currentTime
        } else {
            schedulerTime - currentTime + FULL_DAY_MILLIS
        }
    }
}