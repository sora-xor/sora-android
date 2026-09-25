package jp.co.soramitsu.common.backup

import jp.co.soramitsu.xbackup.BackupService

/** Local wallet creation/import must not initialize optional Google services. */
class CloudBackupProvider(
    val isAvailable: Boolean,
    factory: () -> BackupService,
) {
    private val service by lazy(factory)

    fun serviceOrNull(): BackupService? = if (isAvailable) service else null
}
