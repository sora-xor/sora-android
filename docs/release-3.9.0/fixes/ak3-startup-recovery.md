# ak3 — Startup recovery and transient failure handling

Recovered and integrated locally on 12 October 2026. This is implementation and
local test evidence, not release approval. The recovered key and transaction
branches were merged at `db40b9d4` before these changes.

Startup now preserves a complete raw database generation before SQLite is allowed
to recover a hot rollback journal. It copies the database, sidecars, encrypted
preferences and wrapped-key metadata, verifies every byte and replays the journal
only in a disposable validation copy. Only a complete, verified and durably
published backup authorizes recovery of the unchanged live source. Space for the
complete copy is mandatory. Retained hot-journal backups are validated by replaying
a copy, never by mutating the retained original.

Failure handling distinguishes storage exhaustion and temporary Keystore failures
from database failures. Only separate process starts count toward database recovery
escalation; tapping Try again cannot consume that budget. Repeated database errors
can offer database-only preservation after three failed starts. Storage and
Keystore errors never grant that route. Unreadable preferences must recur across
starts before all-file preservation is offered. The original blocker survives a
later generic migration failure.

The initial Room/account-selection exception boundary and removal preflight now
propagate transient errors instead of recording an irreversible verification or
removal failure. The app owns the migration job independently of splash-screen
cancellation, publishes a fresh result before callers can observe it, and joins
an already-running verification. A completed result is not reused on a later
startup attempt.

Recovery telemetry uses only fixed allowlisted event codes, once per process and
only with diagnostics consent. An explicit opt-out drops pending events. A routine
legacy runtime-cache mismatch is a breadcrumb rather than a crash report.

## Validation

- All 1,766 production-debug unit tests passed, with 17 intentional live/network
  skips. The final account module rerun passed 197 tests, including transient
  removal preflight followed by a successful retry.
- All 120 `WalletUpgradeBackupTest` device tests passed on the dedicated API 34
  emulator. They include real hot-journal rollback, insufficient-space byte
  preservation, cold-start escalation and the original-blocker policy.
- Production-debug app and instrumentation APKs assembled. Final app lint is
  tracked with the production-locale work; its API-33-only exception reference
  was replaced with a class-name check compatible with the minimum API 26.
- Logs are retained in the parent workspace at
  `audit-2026-10-12/android/`. The API 34 evidence is not physical-device or
  actual-store-binary upgrade evidence.

## Still required for release

The actual Play-distributed APK, verified signing/Google identity pins, signed
release packaging, and the physical-device historical upgrade matrix remain
required. No release identity was invented and no release was published. An empty
identity pin intentionally keeps the production release gate closed.
