/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.common.di.modules

import android.content.ClipboardManager
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
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
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
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.inappupdate.InAppUpdateManager
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.io.FileManagerImpl
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.EncryptionUtil
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.xbackup.BackupService
import jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.api.BlockExplorerRepository
import jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.impl.BlockExplorerRepositoryImpl
import jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.api.ConfigDAO
import jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.api.data.ConfigParser
import jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.impl.SuperWalletConfigDAOImpl
import jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.impl.data.RemoteConfigParserImpl
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.models.AbstractRestClientConfig
import jp.co.soramitsu.xnetworking.lib.engines.rest.impl.RestClientImpl
import jp.co.soramitsu.xsubstrate.encrypt.json.JsonSeedEncoder
import kotlinx.serialization.json.Json

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
    fun provideJson(): Json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Singleton
    @Provides
    fun provideConfigParser(
        restClient: RestClient
    ): ConfigParser = RemoteConfigParserImpl(
        restClient = restClient,
        chainsRequestUrl = OptionsProvider.configXn,
    )

    @Singleton
    @Provides
    fun provideConfigDAO(configParser: ConfigParser): ConfigDAO =
        SuperWalletConfigDAOImpl(configParser = configParser)

    @Singleton
    @Provides
    fun provideBlockExplorerRepository(
        configDAO: ConfigDAO,
        restClient: RestClient
    ): BlockExplorerRepository = BlockExplorerRepositoryImpl(
        configDAO = configDAO,
        restClient = restClient
    )

    @Singleton
    @Provides
    fun provideRestClient(json: Json): RestClient = RestClientImpl(
        restClientConfig = object : AbstractRestClientConfig() {
            override fun getConnectTimeoutMillis(): Long = 30_000L
            override fun getOrCreateJsonConfig(): Json = json
            override fun getRequestTimeoutMillis(): Long = 30_000L
            override fun getSocketTimeoutMillis(): Long = 30_000L
            override fun isLoggingEnabled(): Boolean = BuildUtils.isPlayMarket().not()
        }
    )

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
    fun provideClipBoardManager(@ApplicationContext context: Context): BasicClipboardManager {
        return BasicClipboardManager(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
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

    @Provides
    @Singleton
    fun provideBackupService(@ApplicationContext context: Context): BackupService {
        return BackupService.create(BuildConfig.GOOGLE_API_TOKEN, context)
    }
}
