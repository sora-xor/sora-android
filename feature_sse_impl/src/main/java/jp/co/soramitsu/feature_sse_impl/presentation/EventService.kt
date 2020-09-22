package jp.co.soramitsu.feature_sse_impl.presentation

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.data.network.connection.NetworkStateListener
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.feature_sse_api.di.EventFeatureApi
import jp.co.soramitsu.feature_sse_impl.di.EventComponent
import jp.co.soramitsu.feature_sse_impl.domain.EventObserver
import javax.inject.Inject

class EventService : Service() {

    companion object {
        private const val TAG = "EventService"

        fun start(context: Context) {
            val intent = Intent(context, EventService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, EventService::class.java)
            context.stopService(intent)
        }
    }

    private val disposables = CompositeDisposable()

    @Inject lateinit var eventObserver: EventObserver
    @Inject lateinit var networkStateListener: NetworkStateListener

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        FeatureUtils.getFeature<EventComponent>(this, EventFeatureApi::class.java)
            .inject(this)

        disposables.add(
            networkStateListener.subscribe(this)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it == NetworkStateListener.State.CONNECTED) {
                        eventObserver.observeNewEvents()
                    } else {
                        eventObserver.release()
                    }
                }, {
                })
        )
    }

    override fun onDestroy() {
        eventObserver.release()
        networkStateListener.release()
        disposables.dispose()
        super.onDestroy()
    }
}