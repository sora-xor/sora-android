package jp.co.soramitsu.common.delegate

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.interfaces.WithPreloader
import javax.inject.Inject

class WithPreloaderImpl @Inject constructor() : WithPreloader {

    private val preloadVisibilityLiveData = MutableLiveData<Boolean>()

    override fun getPreloadVisibility(): LiveData<Boolean> {
        return preloadVisibilityLiveData
    }

//    override fun <T> preloadCompose(): SingleTransformer<T, T> {
//        return SingleTransformer { single ->
//            single.doOnSubscribe { preloadVisibilityLiveData.postValue(true) }
//                .doAfterTerminate { preloadVisibilityLiveData.postValue(false) }
//        }
//    }
//
//    override fun preloadCompletableCompose(): CompletableTransformer {
//        return CompletableTransformer { completable ->
//            completable.doOnSubscribe { preloadVisibilityLiveData.postValue(true) }
//                .doAfterTerminate { preloadVisibilityLiveData.postValue(false) }
//        }
//    }

    override fun showPreloader() {
        preloadVisibilityLiveData.postValue(true)
    }

    override fun hidePreloader() {
        preloadVisibilityLiveData.postValue(false)
    }
}
