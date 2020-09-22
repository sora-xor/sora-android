package jp.co.soramitsu.common.di.modules

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.AppVersionProviderImpl
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.GsonSerializerImpl
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.data.network.connection.NetworkStateListener
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.DidProvider
import jp.co.soramitsu.common.util.EncryptionUtil
import jp.co.soramitsu.common.util.MnemonicProvider
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.sora.sdk.crypto.json.JSONEd25519Sha3SignatureSuite
import jp.co.soramitsu.sora.sdk.json.JsonUtil
import java.security.SecureRandom
import java.util.Locale
import java.util.TimeZone
import javax.inject.Singleton

const val SHARED_PREFERENCES_FILE = "sora_prefs"

@Module
class CommonModule {

    @Singleton
    @Provides
    fun provideAppVersionProvider(appVersionProvider: AppVersionProviderImpl): AppVersionProvider = appVersionProvider

    @Singleton
    @Provides
    fun providesPushHandler(): PushHandler = PushHandler()

    @Singleton
    @Provides
    fun provideHealthChecker(): HealthChecker = HealthChecker()

    @Singleton
    @Provides
    fun provideNetworkStateListener(): NetworkStateListener = NetworkStateListener()

    @Singleton
    @Provides
    fun provideQrCodeGenerator(resourceManager: ResourceManager): QrCodeGenerator {
        return QrCodeGenerator(Color.BLACK, resourceManager.getColor(R.color.qrCodeSecondColor))
    }

    @Singleton
    @Provides
    fun provideDeviceParams(context: Context): DeviceParamsProvider {
        return DeviceParamsProvider(
            context.resources.displayMetrics.widthPixels,
            context.resources.displayMetrics.heightPixels,
            Build.MODEL,
            Build.VERSION.RELEASE,
            Locale.getDefault().language,
            Locale.getDefault().country,
            TimeZone.getDefault()
        )
    }

    @Singleton
    @Provides
    fun provideMnemonicProvider(): MnemonicProvider = MnemonicProvider()

    @Singleton
    @Provides
    fun provideSecureRandom(): SecureRandom = SecureRandom()

    @Singleton
    @Provides
    fun provideCryptoAssistant(secureRandom: SecureRandom): CryptoAssistant {
        return CryptoAssistant(secureRandom, JsonUtil.buildMapper(), JSONEd25519Sha3SignatureSuite(), Ed25519Sha3())
    }

    @Provides
    @Singleton
    fun provideDidProvider(): DidProvider {
        return DidProvider(JsonUtil.buildMapper())
    }

    @Provides
    @Singleton
    fun provideInvitationHandler(): InvitationHandler = InvitationHandler()

    @Provides
    @Singleton
    fun provideDebounceClickHandler(): DebounceClickHandler = DebounceClickHandler()

    @Provides
    @Singleton
    fun provideSerializer(): Serializer = GsonSerializerImpl(Gson())

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun providePreferences(prefs: SharedPreferences): Preferences {
        return Preferences(prefs)
    }

    @Provides
    @Singleton
    fun provideEncryptedPreferences(preferences: Preferences, encryptionUtil: EncryptionUtil): EncryptedPreferences {
        return EncryptedPreferences(preferences, encryptionUtil)
    }

    @Provides
    @Singleton
    fun provideNumbersFormatter(): NumbersFormatter = NumbersFormatter()

    @Provides
    @Singleton
    fun provideTextFormatter(): TextFormatter = TextFormatter()

    @Provides
    fun provideLocale(contextManager: ContextManager): Locale {
        return contextManager.getLocale()
    }

    @Provides
    fun provideDateTimeFormatter(locale: Locale, resourceManager: ResourceManager): DateTimeFormatter {
        return DateTimeFormatter(locale, resourceManager)
    }

    @Provides
    @Singleton
    fun provideClipBoardManager(context: Context): ClipboardManager {
        return ClipboardManager(context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
    }

    @Provides
    @Singleton
    fun provideLanguagesHolder(): LanguagesHolder {
        return LanguagesHolder()
    }

    @Provides
    @Singleton
    fun provideAssetHolder(): AssetHolder {
        return AssetHolder()
    }
}