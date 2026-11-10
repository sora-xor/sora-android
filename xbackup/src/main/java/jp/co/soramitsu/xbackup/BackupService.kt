/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import jp.co.soramitsu.xbackup.domain.AccountDatasource
import jp.co.soramitsu.xbackup.domain.AccountJsonMapper
import jp.co.soramitsu.xbackup.domain.BackupAccountCrypto
import jp.co.soramitsu.xbackup.domain.Storage
import jp.co.soramitsu.xbackup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.xbackup.domain.exceptions.DecodingException
import jp.co.soramitsu.xbackup.domain.exceptions.DecryptionException
import jp.co.soramitsu.xbackup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.xbackup.domain.exceptions.InsufficientFilePermissions
import jp.co.soramitsu.xbackup.domain.exceptions.StorageQuotaExceeded
import jp.co.soramitsu.xbackup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.xbackup.domain.models.BackupAccountMeta
import jp.co.soramitsu.xbackup.domain.models.BackupAccountType
import jp.co.soramitsu.xbackup.domain.models.CryptoType
import jp.co.soramitsu.xbackup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.xbackup.domain.models.FileMeta
import jp.co.soramitsu.xbackup.domain.models.Json
import jp.co.soramitsu.xbackup.auth.AccountManagerTokenSource
import jp.co.soramitsu.xbackup.auth.PrefsGoogleAccountSource
import jp.co.soramitsu.xbackup.repository.GoogleDriveStorage
import jp.co.soramitsu.xbackup.repository.PrefsAccountDatasource

class BackupService internal constructor(
    private val context: Context,
    private val storage: Storage,
    private val accountJsonMapper: AccountJsonMapper,
    private val backupAccountCrypto: BackupAccountCrypto,
    private val accountDatastore: AccountDatasource,
) {
    suspend fun authorize(launcher: ActivityResultLauncher<Intent>): Boolean {
        return storage.signIn(context, launcher)
    }

    @Throws(
        UnauthorizedException::class,
        FileNotFoundException::class,
        AuthConsentException::class,
        StorageQuotaExceeded::class,
        InsufficientFilePermissions::class,
    )
    suspend fun saveBackupAccount(
        account: DecryptedBackupAccount,
        password: String,
    ) {
        val encryptedBackupAccount = backupAccountCrypto.encryptAccount(account, password)
        val file =
            accountJsonMapper.mapEncryptedAccountToJsonFile(
                context.cacheDir.absolutePath + java.io.File.separator,
                encryptedBackupAccount,
            )

        val fileId =
            try {
                storage.saveFile(
                    context = context,
                    name = "${account.address}.json",
                    description = account.name,
                    file = file,
                )
            } finally {
                file.delete()
            }

        accountDatastore.saveFileId(account.address, fileId)
    }

    @Throws(
        UnauthorizedException::class,
        FileNotFoundException::class,
        AuthConsentException::class,
        InsufficientFilePermissions::class,
    )
    suspend fun getBackupAccounts(): List<BackupAccountMeta> {
        accountDatastore.removeAllFields()

        return storage.getListOfFileHeads(context).map {
            val address = it.fileName.substringBefore(".json")
            accountDatastore.saveFileId(address, it.fileId)

            BackupAccountMeta(
                name = it.description,
                address = it.fileName.substringBefore(".json"),
            )
        }
    }

    @Throws(UnauthorizedException::class, DecodingException::class, DecryptionException::class)
    suspend fun importBackupAccount(
        address: String,
        password: String,
    ): DecryptedBackupAccount {
        val fileId = accountDatastore.getFileId(address)
        val fileContent = storage.getFileContent(context, fileId)
        val encryptedBackupAccount = accountJsonMapper.mapJsonStringToEncryptedAccount(fileContent)
        return backupAccountCrypto.decryptAccount(encryptedBackupAccount, password)
    }

    @Throws(UnauthorizedException::class, AuthConsentException::class)
    suspend fun getWebBackupAccounts(): List<BackupAccountMeta> {
        val webListOfFileHeads = storage.getWebListOfFileHeads(context)
        val ethereumFileIds = webListOfFileHeads.mapNotNull(::getWebEthereumFileId)

        return webListOfFileHeads.filter { it.fileId !in ethereumFileIds }.map {
            createWebBackupAccountMeta(it)
        }
    }

    private fun getWebEthereumFileId(it: FileMeta) =
        // web description format: <substrate google backup address>[/<ethereum json backup fileId>]
        it.description.split("/").getOrNull(1)

    private suspend fun createWebBackupAccountMeta(it: FileMeta): BackupAccountMeta {
        // web description format: <substrate google backup address>[/<ethereum json backup fileId>]
        val address = it.description.substringBefore("/")
        val ethereumFileId = it.description.split("/").getOrNull(1)
        // combine fileIds, so we can reach them both at import
        val combinedFileId = it.fileId + ethereumFileId?.let { "/$ethereumFileId" }.orEmpty()

        if (accountDatastore.getFileId(address).isEmpty()) {
            accountDatastore.saveFileId(address, combinedFileId)
        }

        return BackupAccountMeta(
            name = it.fileName.substringBefore(".json"),
            address = address,
        )
    }

    @Throws(UnauthorizedException::class, DecodingException::class, DecryptionException::class)
    suspend fun importWebBackupAccount(
        address: String,
        name: String,
    ): DecryptedBackupAccount {
        val combinedFileId = accountDatastore.getFileId(address)
        val (substrateJsonFileId, ethereumJsonFileId) = getWebFileIds(combinedFileId)

        val substrateJson = storage.getFileContent(context, substrateJsonFileId)
        val ethereumJson = ethereumJsonFileId?.let { storage.getFileContent(context, it) }
        return DecryptedBackupAccount(
            name = name,
            address = address,
//            any value, cryptoType not used when importing json
            cryptoType = CryptoType.ED25519,
            backupAccountType = listOf(BackupAccountType.JSON),
            json = Json(substrateJson, ethereumJson),
        )
    }

    private fun getWebFileIds(combinedFileId: String): Pair<String, String?> {
        // combinedFileId format: <substrate_fileId>[/<ethereum_fileId>] see createWebBackupAccountMeta()
        return with(combinedFileId.split("/")) {
            get(0) to getOrNull(1)
        }
    }

    suspend fun isAccountBackedUp(address: String): Boolean {
        return accountDatastore.getFileId(address).isNotEmpty()
    }

    @Throws(
        UnauthorizedException::class,
        AuthConsentException::class,
        FileNotFoundException::class,
        InsufficientFilePermissions::class,
    )
    suspend fun deleteBackupAccount(address: String) {
        val fileId = accountDatastore.getFileId(address)
        try {
            storage.deleteFile(context, fileId)
            accountDatastore.removeFileId(address)
        } catch (e: FileNotFoundException) {
            accountDatastore.removeFileId(address)
            throw e
        }
    }

    @Throws(
        UnauthorizedException::class,
        FileNotFoundException::class,
        AuthConsentException::class,
        InsufficientFilePermissions::class,
    )
    suspend fun deleteAllAccounts() {
        storage.deleteAllFiles(context)
        accountDatastore.removeAllFields()
    }

    suspend fun logout() {
        accountDatastore.removeAllFields()
        storage.logout(context)
    }

    companion object {
        /**
         * Drive access needs no client ID: Google identifies the app by its
         * package name and signing certificate (its Android OAuth client).
         */
        fun create(context: Context): BackupService {
            return BackupService(
                context.applicationContext,
                GoogleDriveStorage(
                    PrefsGoogleAccountSource(context),
                    AccountManagerTokenSource(context),
                ),
                AccountJsonMapper(),
                BackupAccountCrypto(),
                PrefsAccountDatasource(context),
            )
        }
    }
}
