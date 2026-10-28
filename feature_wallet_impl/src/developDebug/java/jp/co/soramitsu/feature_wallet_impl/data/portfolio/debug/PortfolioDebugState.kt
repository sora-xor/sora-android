package jp.co.soramitsu.feature_wallet_impl.data.portfolio.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.domain.WhitelistTokensManager
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PinnedTilt
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PortfolioDebugHooks
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PortfolioSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Debug switches for the Portfolio view, compiled only into developDebug. They are kept in this
 * build's own preferences file and set from adb (see [PortfolioDebugReceiver]).
 */
@Singleton
class PortfolioDebugState @Inject constructor(
    @ApplicationContext context: Context,
    private val whitelistTokensManager: WhitelistTokensManager,
) : PortfolioDebugHooks {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val sample = MutableStateFlow<PortfolioSource?>(null)
    private val tilt = MutableStateFlow(parseTilt(prefs.getString(KEY_TILT, null)))

    override val sampleSource: StateFlow<PortfolioSource?> = sample.asStateFlow()
    override val pinnedTilt: StateFlow<PinnedTilt?> = tilt.asStateFlow()

    init {
        rebuildSample()
    }

    fun apply(sampleMode: String?, freshness: String?, tiltValue: String?) {
        prefs.edit {
            sampleMode?.let { putString(KEY_SAMPLE, it.lowercase()) }
            freshness?.let { putString(KEY_FRESHNESS, it.lowercase()) }
            tiltValue?.let { putString(KEY_TILT, it) }
        }
        if (sampleMode != null || freshness != null) rebuildSample()
        if (tiltValue != null) tilt.value = parseTilt(tiltValue)
    }

    private fun rebuildSample() {
        val mode = when (prefs.getString(KEY_SAMPLE, OFF)) {
            "full", "on" -> SampleMode.FULL
            "empty" -> SampleMode.EMPTY
            else -> null
        }
        val freshness = SampleFreshness.entries.firstOrNull {
            it.name.equals(prefs.getString(KEY_FRESHNESS, null), ignoreCase = true)
        } ?: SampleFreshness.LIVE
        sample.value = mode?.let {
            SamplePortfolioSource(it, freshness, whitelistTokensManager::getTokenIconUri)
        }
    }

    private fun parseTilt(value: String?): PinnedTilt? {
        val parts = value?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() } ?: return null
        if (parts.size != 2) return null
        return PinnedTilt(parts[0].coerceIn(-1.0, 1.0), parts[1].coerceIn(-1.0, 1.0))
    }

    private companion object {
        const val PREFS = "portfolio_debug"
        const val KEY_SAMPLE = "sample"
        const val KEY_FRESHNESS = "freshness"
        const val KEY_TILT = "tilt"
        const val OFF = "off"
    }
}

/**
 * adb-only debug hook (the receiver requires android.permission.DUMP, which the shell holds and
 * apps do not):
 *
 * adb shell am broadcast -n <applicationId>/jp.co.soramitsu.feature_wallet_impl.data.portfolio.debug.PortfolioDebugReceiver \
 *     --es sample full|empty|off --es freshness live|saved|partial|offline --es tilt 0.6,-0.3|off
 */
class PortfolioDebugReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access {
        fun portfolioDebugState(): PortfolioDebugState
    }

    override fun onReceive(context: Context, intent: Intent) {
        val state = EntryPointAccessors.fromApplication(context.applicationContext, Access::class.java)
            .portfolioDebugState()
        state.apply(
            sampleMode = intent.getStringExtra("sample"),
            freshness = intent.getStringExtra("freshness"),
            tiltValue = intent.getStringExtra("tilt"),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PortfolioDebugModule {
    @Binds
    abstract fun bindPortfolioDebugHooks(state: PortfolioDebugState): PortfolioDebugHooks
}
