/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.di.modules

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Vibrator
import coil.decode.SvgDecoder
import com.google.gson.Gson
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Base64MessageEncoder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Locale
import java.util.TimeZone
import javax.inject.Singleton
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.data.AppStateProviderImpl
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.data.network.connection.NetworkStateListener
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.delegate.WithProgressImpl
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.inappupdate.InAppUpdateManager
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.io.FileManagerImpl
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.EncryptionUtil
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuHttpClientProvider
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuHttpClientProviderImpl
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuNetworkClient
import jp.co.soramitsu.xnetworking.sorawallet.tokenwhitelist.SoraTokensWhitelistManager
import jp.co.soramitsu.xnetworking.txhistory.client.sorawallet.SubQueryClientForSoraWalletFactory

@InstallIn(SingletonComponent::class)
@Module
class CommonActivityModule {

    @Singleton
    @Provides
    fun provideDateTimeFormatter(
        @ApplicationContext context: Context,
        languagesHolder: LanguagesHolder,
        resourceManager: ResourceManager,
    ): DateTimeFormatter {
        return DateTimeFormatter(languagesHolder, resourceManager, context)
    }

    @Singleton
    @Provides
    fun provideResourceManager(): ResourceManager {
        return ResourceManager()
    }

    @Singleton
    @Provides
    fun provideLanguageHolder(): LanguagesHolder {
        return LanguagesHolder()
    }
}

@InstallIn(SingletonComponent::class)
@Module
class CommonModule {

    @Singleton
    @Provides
    fun provideSoraPreferences(@ApplicationContext c: Context): SoraPreferences = SoraPreferences(c)

    @Singleton
    @Provides
    fun provideEncryptionUtil(@ApplicationContext c: Context): EncryptionUtil = EncryptionUtil(c)

    @Singleton
    @Provides
    fun provideSvgDecoder(): SvgDecoder.Factory {
        return SvgDecoder.Factory()
    }

    @Singleton
    @Provides
    fun provideInAppUpdateManager(
        @ApplicationContext c: Context,
        sp: SoraPreferences
    ): InAppUpdateManager = InAppUpdateManager(c, sp)

    @Singleton
    @Provides
    fun provideAppStateManager(): AppStateProvider = AppStateProviderImpl()

    @Singleton
    @Provides
    fun provideCoroutineManager(): CoroutineManager =
        CoroutineManager()

    @Provides
    fun provideWithProgress(): WithProgress = WithProgressImpl()

    @Singleton
    @Provides
    fun providesPushHandler(): PushHandler = PushHandler()

    @Singleton
    @Provides
    fun provideNetworkStateListener(): NetworkStateListener = NetworkStateListener()

    @Singleton
    @Provides
    fun provideQrCodeGenerator(): QrCodeGenerator {
        return QrCodeGenerator(Color.BLACK)
    }

    @Singleton
    @Provides
    fun provideSoramitsuNetworkClient(): SoramitsuNetworkClient =
        SoramitsuNetworkClient(logging = BuildConfig.DEBUG)

    @Singleton
    @Provides
    fun provideSoramitsuHttpClientProvider(): SoramitsuHttpClientProvider =
        SoramitsuHttpClientProviderImpl()

    @Singleton
    @Provides
    fun provideSubQueryClientForSoraWalletFactory(
        @ApplicationContext context: Context
    ): SubQueryClientForSoraWalletFactory = SubQueryClientForSoraWalletFactory(context)

    @Singleton
    @Provides
    fun provideSoraTokensWhitelistFetcher(
        client: SoramitsuNetworkClient,
    ): SoraTokensWhitelistManager =
        SoraTokensWhitelistManager(networkClient = client)

    @Singleton
    @Provides
    fun provideFileManager(@ApplicationContext context: Context): FileManager =
        FileManagerImpl(context)

    @Singleton
    @Provides
    fun provideDeviceParams(@ApplicationContext context: Context): DeviceParamsProvider {
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
    fun provideSecureRandom(): SecureRandom = SecureRandom()

    @Singleton
    @Provides
    fun provideCryptoAssistant(secureRandom: SecureRandom): CryptoAssistant {
        return CryptoAssistant(
            secureRandom, Ed25519Sha3(),
            LazySodiumAndroid(
                SodiumAndroid(),
                StandardCharsets.UTF_8,
                Base64MessageEncoder()
            )
        )
    }

    @Provides
    @Singleton
    fun provideInvitationHandler(): InvitationHandler = InvitationHandler()

    @Provides
    @Singleton
    fun provideJsonMapper(): Gson = Gson()

    @Provides
    @Singleton
    fun provideJsonAccountsEncoder(
        gson: Gson,
        cryptoAssistant: CryptoAssistant,
        jsonSeedEncoder: JsonSeedEncoder
    ): JsonAccountsEncoder = JsonAccountsEncoder(gson, cryptoAssistant, jsonSeedEncoder)

    @Provides
    @Singleton
    fun provideJsonSeedEncoder(gson: Gson): JsonSeedEncoder = JsonSeedEncoder(gson)

    @Provides
    @Singleton
    fun provideEncryptedPreferences(
        soraPreferences: SoraPreferences,
        encryptionUtil: EncryptionUtil
    ): EncryptedPreferences {
        return EncryptedPreferences(soraPreferences, encryptionUtil)
    }

    @Provides
    @Singleton
    fun provideNumbersFormatter(): NumbersFormatter = NumbersFormatter()

    @Provides
    @Singleton
    fun provideTextFormatter(): TextFormatter = TextFormatter()

    @Provides
    @Singleton
    fun provideClipBoardManager(@ApplicationContext context: Context): ClipboardManager {
        return ClipboardManager(context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
    }

    @Provides
    @Singleton
    fun provideAccountAvatar(rm: ResourceManager): AccountAvatarGenerator {
        return AccountAvatarGenerator(rm)
    }

    @Provides
    @Singleton
    fun provideDeviceVibrator(@ApplicationContext context: Context): DeviceVibrator {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        return DeviceVibrator(vibrator)
    }
}
