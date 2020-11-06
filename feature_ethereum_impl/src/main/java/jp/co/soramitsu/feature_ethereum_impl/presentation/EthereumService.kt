package jp.co.soramitsu.feature_ethereum_impl.presentation

import android.app.IntentService
import android.content.Context
import android.content.Intent
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.feature_ethereum_api.di.EthereumFeatureApi
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_impl.di.EthereumFeatureComponent
import javax.inject.Inject

class EthereumService : IntentService(ETH_REGISTRATION_SERVICE_NAME) {

    companion object {
        private const val ETH_REGISTRATION_SERVICE_NAME = "EthRegistrationService"
        private const val ACTION_RETRY_REGISTER = "jp.co.soramitsu.feature_ethereum_impl.RETRY_ETH_REGISTER"
        private const val ACTION_REGISTER = "jp.co.soramitsu.feature_ethereum_impl_ETH_REGISTER"

        fun start(context: Context) {
            val intent = Intent(context, EthereumService::class.java).apply {
                action = ACTION_REGISTER
            }
            context.startService(intent)
        }

        fun startForRetry(context: Context) {
            val intent = Intent(context, EthereumService::class.java).apply {
                action = ACTION_RETRY_REGISTER
            }
            context.startService(intent)
        }
    }

    @Inject lateinit var ethInteractor: EthereumInteractor

    override fun onCreate() {
        super.onCreate()
        FeatureUtils.getFeature<EthereumFeatureComponent>(this, EthereumFeatureApi::class.java)
            .ethereumServiceComponentBuilder()
            .build()
            .inject(this)
    }

    override fun onHandleIntent(intent: Intent?) {
        try {
            if (ACTION_RETRY_REGISTER == intent?.action) {
                ethInteractor.registerEthAccount().blockingAwait()
            } else {
                val ethRegisterState = ethInteractor.getActualEthRegisterState().blockingGet()
                if (EthRegisterState.State.NONE == ethRegisterState) {
                    ethInteractor.registerEthAccount().blockingAwait()
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}