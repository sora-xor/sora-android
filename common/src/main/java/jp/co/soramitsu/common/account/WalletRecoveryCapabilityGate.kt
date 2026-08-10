package jp.co.soramitsu.common.account

/**
 * Process-wide capability boundary used while wallet modernization is not verified.
 *
 * The durable source of truth remains the migration journal. This in-memory gate is populated
 * from that journal before dependency injection can open Room, and prevents an already-open
 * process from bypassing the same recovery state. Only the migration coordinator may use
 * [requireMigrationWriteAllowed]; every user initiated wallet/secret mutation and signer must use
 * [requireUserMutationAllowed].
 */
object WalletRecoveryCapabilityGate {

    enum class Mode {
        NORMAL,
        BLOCKED,
        RECOVERY_INSPECTED,
        LEGACY_READ_ONLY,
        MIGRATION_RETRY_AUTHORIZED,
    }

    @Volatile
    private var mode: Mode = Mode.NORMAL

    fun mode(): Mode = mode

    @Synchronized
    fun enterNormal() {
        mode = Mode.NORMAL
    }

    @Synchronized
    fun enterBlocked() {
        mode = Mode.BLOCKED
    }

    @Synchronized
    fun enterInspectedRecovery() {
        mode = Mode.RECOVERY_INSPECTED
    }

    @Synchronized
    fun enterLegacyReadOnly() {
        check(
            mode == Mode.BLOCKED ||
                mode == Mode.RECOVERY_INSPECTED ||
                mode == Mode.LEGACY_READ_ONLY
        ) {
            "WALLET_RECOVERY_MODE_TRANSITION_INVALID"
        }
        mode = Mode.LEGACY_READ_ONLY
    }

    @Synchronized
    fun authorizeMigrationRetry() {
        check(
            mode == Mode.BLOCKED ||
                mode == Mode.RECOVERY_INSPECTED ||
                mode == Mode.LEGACY_READ_ONLY ||
                mode == Mode.MIGRATION_RETRY_AUTHORIZED
        ) {
            "WALLET_RECOVERY_MODE_TRANSITION_INVALID"
        }
        mode = Mode.MIGRATION_RETRY_AUTHORIZED
    }

    fun isLegacyReadOnly(): Boolean = mode == Mode.LEGACY_READ_ONLY

    fun requiresWalletWriteGuards(): Boolean =
        mode == Mode.RECOVERY_INSPECTED || mode == Mode.LEGACY_READ_ONLY

    fun isMigrationRetryAuthorized(): Boolean =
        mode == Mode.MIGRATION_RETRY_AUTHORIZED

    fun mayResumeWalletDeletion(): Boolean = mode == Mode.NORMAL

    fun requireDatabaseAccessAllowed() {
        check(
            mode == Mode.NORMAL ||
                mode == Mode.RECOVERY_INSPECTED ||
                mode == Mode.LEGACY_READ_ONLY ||
                mode == Mode.MIGRATION_RETRY_AUTHORIZED
        ) { "WALLET_DATABASE_RECOVERY_BLOCKED" }
    }

    fun requireUserMutationAllowed() {
        check(mode == Mode.NORMAL) { "WALLET_RECOVERY_READ_ONLY" }
    }

    fun requireMigrationWriteAllowed() {
        check(
            mode == Mode.NORMAL || mode == Mode.MIGRATION_RETRY_AUTHORIZED
        ) { "WALLET_MIGRATION_RETRY_NOT_AUTHORIZED" }
    }
}
