/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.di.modules

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Vibrator
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.data.AppStateProviderImpl
import jp.co.soramitsu.common.data.AppVersionProviderImpl
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.GsonSerializerImpl
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.data.network.Sora2CoroutineApiCreator
import jp.co.soramitsu.common.data.network.connection.NetworkStateListener
import jp.co.soramitsu.common.data.network.substrate.ConnectionManager
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeManager
import jp.co.soramitsu.common.data.network.substrate.runtime.SubstrateTypesApi
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.io.FileManagerImpl
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.EncryptionUtil
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import java.security.SecureRandom
import java.util.Locale
import java.util.TimeZone
import javax.inject.Singleton

@Module
class CommonModule {

    @Singleton
    @Provides
    fun provideAppVersionProvider(appVersionProvider: AppVersionProviderImpl): AppVersionProvider =
        appVersionProvider

    @Singleton
    @Provides
    fun provideAppStateManager(appStateManager: AppStateProviderImpl): AppStateProvider =
        appStateManager

    @Singleton
    @Provides
    fun provideCoroutineManager(): CoroutineManager =
        CoroutineManager()

    @Singleton
    @Provides
    fun providesPushHandler(): PushHandler = PushHandler()

    @Singleton
    @Provides
    fun provideHealthChecker(cm: ConnectionManager): HealthChecker = HealthChecker(cm)

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
    fun provideTypesApi(sora2CoroutineApiCreator: Sora2CoroutineApiCreator): SubstrateTypesApi =
        sora2CoroutineApiCreator.create(SubstrateTypesApi::class.java)

    @Singleton
    @Provides
    fun provideRuntimeManager(
        fileManager: FileManager,
        gson: Gson,
        soraPreferences: SoraPreferences,
        socketService: SocketService,
        typesApi: SubstrateTypesApi,
    ): RuntimeManager =
        RuntimeManager(fileManager, gson, soraPreferences, socketService, typesApi)

    @Singleton
    @Provides
    fun provideFileManager(context: Context): FileManager =
        FileManagerImpl(context)

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
    fun provideSecureRandom(): SecureRandom = SecureRandom()

    @Singleton
    @Provides
    fun provideCryptoAssistant(secureRandom: SecureRandom): CryptoAssistant {
        return CryptoAssistant(secureRandom, Ed25519Sha3())
    }

    @Provides
    @Singleton
    fun provideInvitationHandler(): InvitationHandler = InvitationHandler()

    @Provides
    @Singleton
    fun provideDebounceClickHandler(): DebounceClickHandler = DebounceClickHandler()

    @Provides
    @Singleton
    fun provideJsonMapper(): Gson = Gson()

    @Provides
    @Singleton
    fun provideSerializer(gson: Gson): Serializer = GsonSerializerImpl(gson)

    @Provides
    @Singleton
    fun providePreferences(
        context: Context
    ): SoraPreferences {
        return SoraPreferences(context)
    }

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
    fun provideLocale(contextManager: ContextManager): Locale {
        return contextManager.getLocale()
    }

    @Provides
    fun provideDateTimeFormatter(
        locale: Locale,
        resourceManager: ResourceManager,
        context: Context,
    ): DateTimeFormatter {
        return DateTimeFormatter(locale, resourceManager, context)
    }

    @Provides
    @Singleton
    fun provideClipBoardManager(context: Context): ClipboardManager {
        return ClipboardManager(context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
    }

    @Provides
    @Singleton
    fun provideAssetHolder(): AssetHolder {
        return AssetHolder()
    }

    @Provides
    @Singleton
    fun provideAccountAvatar(rm: ResourceManager): AccountAvatarGenerator {
        return AccountAvatarGenerator(rm)
    }

    @Provides
    @Singleton
    fun provideDeviceVibrator(context: Context): DeviceVibrator {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        return DeviceVibrator(vibrator)
    }
}
