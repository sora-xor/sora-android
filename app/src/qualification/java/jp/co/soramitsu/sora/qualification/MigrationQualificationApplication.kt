package jp.co.soramitsu.sora.qualification

import android.app.Application

/**
 * Minimal application for the package-isolated migration qualification flavor.
 *
 * The production Hilt/startup graph must not open Room, schedule work, or inspect wallet state
 * before instrumentation has proved that it is running under the isolated application ID.
 */
class MigrationQualificationApplication : Application()
