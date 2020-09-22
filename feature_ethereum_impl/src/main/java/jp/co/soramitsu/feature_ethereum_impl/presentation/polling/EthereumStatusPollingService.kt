package jp.co.soramitsu.feature_ethereum_impl.presentation.polling

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.feature_ethereum_api.di.EthereumFeatureApi
import jp.co.soramitsu.feature_ethereum_impl.di.EthereumFeatureComponent
import jp.co.soramitsu.feature_ethereum_impl.domain.EthereumStatusObserver
import javax.inject.Inject

class EthereumStatusPollingService : Service() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, EthereumStatusPollingService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, EthereumStatusPollingService::class.java)
            context.stopService(intent)
        }
    }

    @Inject lateinit var ethereumStatusObserver: EthereumStatusObserver

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        FeatureUtils.getFeature<EthereumFeatureComponent>(this, EthereumFeatureApi::class.java)
            .ethereumStatusPollingServiceComponentBuilder()
            .build()
            .inject(this)
        ethereumStatusObserver.syncEthereumTransactionStatuses()
    }

    override fun onDestroy() {
        ethereumStatusObserver.release()
        super.onDestroy()
    }
}