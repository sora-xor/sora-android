/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.activity

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.ext.date2Day
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeed
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import java.util.Calendar

class ActivityFeedViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val preloader: WithPreloader,
    private val resourceManager: ResourceManager
) : BaseViewModel(), WithPreloader by preloader {

    companion object {
        private const val ACTIVITIES_PER_PAGE = 50
    }

    val activityFeedLiveData = MutableLiveData<List<Any>>()
    val showToolbarLiveData = MutableLiveData<Boolean>()
    val showEmptyLiveData = MutableLiveData<Boolean>()

    private var activitiesOffset = 0
    private var loading = false
    private var lastPageLoaded = false

    private val activityFeedList = mutableListOf<Any>()

    fun refreshData(showLoading: Boolean, updateCached: Boolean) {
        if (showLoading) {
            preloader.showPreloader()
        }
        activitiesOffset = 0
        lastPageLoaded = false

        activityFeedLiveData.value?.let { if (it.isNotEmpty()) preloader.hidePreloader() }

        disposables.add(
            interactor.getActivityFeedWithAnnouncement(updateCached, ACTIVITIES_PER_PAGE, activitiesOffset)
                .doOnSuccess { activitiesOffset += ACTIVITIES_PER_PAGE }
                .doFinally { if (!updateCached) refreshData(false, true) }
                .subscribeOn(Schedulers.io())
                .map { activitiesWithAnnouncement ->
                    mutableListOf<Any>().apply {
                        add(ActivityHeader(resourceManager.getString(R.string.activity)))
                        addAll(activitiesWithAnnouncement)
                    }
                }
                .doOnSuccess {
                    activityFeedList.clear()
                    activityFeedList.addAll(it)
                }
                .map { addDatesToActivityFeed(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    preloader.hidePreloader()
                    activityFeedLiveData.value = it
                    showEmptyLiveData.value = it.size == 1
                }, {
                    logException(it)
                })
        )
    }

    fun loadMoreActivity() {
        if (loading || lastPageLoaded) return
        disposables.add(
            interactor.getActivityFeed(true, ACTIVITIES_PER_PAGE, activitiesOffset)
                .doOnSuccess {
                    activitiesOffset += ACTIVITIES_PER_PAGE
                    if (it.isEmpty()) lastPageLoaded = true
                }
                .doOnSubscribe { loading = true }
                .subscribeOn(Schedulers.io())
                .map { newActivities ->
                    mutableListOf<Any>().apply {
                        addAll(activityFeedList)
                        addAll(newActivities)
                    }
                }
                .doOnSuccess {
                    activityFeedList.clear()
                    activityFeedList.addAll(it)
                }
                .map { addDatesToActivityFeed(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    loading = false
                    activityFeedLiveData.value = it
                }, {
                    logException(it)
                })
        )
    }

    private fun addDatesToActivityFeed(feed: List<Any>): List<Any> {
        val list = mutableListOf<Any>()
        var lastDate = 0L

        feed.forEachIndexed { index, item ->
            if (item is ActivityFeed) {
                if (lastDate == 0L) {
                    val dateStr = item.issuedAt.date2Day(resourceManager.getString(R.string.today), resourceManager.getString(R.string.yesterday))
                    list.add(ActivityDate(dateStr))
                } else {
                    if (differentDays(lastDate, item.issuedAt.time)) {
                        val dateStr = item.issuedAt.date2Day(resourceManager.getString(R.string.today), resourceManager.getString(R.string.yesterday))
                        list.add(ActivityDate(dateStr))
                    }
                }
                lastDate = item.issuedAt.time
            }

            val activityFeedListItem = if (item is ActivityFeed) {
                val previousItem = list.lastOrNull()
                val nextItem = feed.getOrNull(index + 1)
                val previousIsDate = previousItem != null && previousItem is ActivityDate
                val nextItemDayChanged = nextItem == null || (differentDays((nextItem as ActivityFeed).issuedAt.time, lastDate))

                if (previousIsDate) {
                    if (nextItemDayChanged) {
                        mapActivityFeedToActivityFeedItem(item, ActivityFeedItem.Type.THE_ONLY_EVENT_OF_THE_DAY)
                    } else {
                        mapActivityFeedToActivityFeedItem(item, ActivityFeedItem.Type.LAST_OF_THE_DAY)
                    }
                } else {
                    if (nextItemDayChanged) {
                        mapActivityFeedToActivityFeedItem(item, ActivityFeedItem.Type.FIRST_OF_THE_DAY)
                    } else {
                        mapActivityFeedToActivityFeedItem(item, ActivityFeedItem.Type.DURING_THE_DAY)
                    }
                }
            } else {
                item
            }

            list.add(activityFeedListItem)
        }
        return list
    }

    private fun mapActivityFeedToActivityFeedItem(activityFeed: ActivityFeed, listItemType: ActivityFeedItem.Type): ActivityFeedItem {
        return with(activityFeed) {
            ActivityFeedItem(type, title, description, votesString, issuedAt, iconDrawable, listItemType)
        }
    }

    private fun differentDays(one: Long, two: Long): Boolean {
        val firstCalendar = Calendar.getInstance().apply {
            timeInMillis = one
        }
        val secondCalendar = Calendar.getInstance().apply {
            timeInMillis = two
        }

        return firstCalendar.get(Calendar.DAY_OF_YEAR) != secondCalendar.get(Calendar.DAY_OF_YEAR)
    }

    fun btnHelpClicked() {
        router.showFaq()
    }

    fun onScrolled(dy: Int) {
        if (dy > 0) {
            if (showToolbarLiveData.value == null) {
                showToolbarLiveData.value = true
            } else {
                val toolbarShowed = showToolbarLiveData.value!!
                if (!toolbarShowed) {
                    showToolbarLiveData.value = true
                }
            }
        } else {
            if (showToolbarLiveData.value == null) {
                showToolbarLiveData.value = false
            } else {
                val toolbarShowed = showToolbarLiveData.value!!
                if (toolbarShowed) {
                    showToolbarLiveData.value = false
                }
            }
        }
    }
}