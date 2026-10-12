# ak4 — Reversible wallet generations and Keystore fault recovery

Recovered and integrated locally on 12 October 2026. This document records local
implementation and evidence; it does not declare the release ready.

A temporary null response from an Android Keystore provider is no longer treated
as proof that the existing wrapping key is permanently gone. Null/missing-key and
unclassified metadata observations remain transient within the current process
and must recur in a fresh process before a permanent recovery route is offered.
Known transient provider errors remain retryable. A read-only inspection never
creates keys or changes the observation record. Malformed or unreadable raw key
metadata cannot be replaced on the strength of a cached empty SharedPreferences
object. The authoritative `.bak` is included, and preference/database reads use
bounded, no-follow file inspection.

Each generation records its wrapping alias in `key_alias.xml`. Missing alias
metadata means the historical `key_alias`. A fresh wallet uses a fresh UUID alias
when an earlier generation owns the old alias. Retained backups, partial folders
and `.bak` files all participate in that guard; unavailable inventory fails
closed. Old aliases and encrypted bytes are not overwritten.

Set-up-again requests are now durable operations with a stable ID, destination and
complete path/size/SHA-256 manifest written before the first rename. A restart
resumes that operation rather than starting another folder. Every move requires
exactly one verified copy at the source or destination, uses no-overwrite checks,
and syncs the affected directories. Before the first move, a cold start rechecks
the original failure. A healed key or readable preferences cancel the request;
a transient recheck keeps everything in place. An unstarted request can also be
cancelled from the pending recovery screen.

Preserved generations remain selectable from onboarding, Login & Security and
recovery even after a new live wallet exists. The switch requires the phone's
screen lock, preserves the current generation first, restarts the process and
then reactivates the selected generation. Its original PIN remains in effect.
The all-file restore confirmation explicitly requires a recovery phrase or
working backup for every wallet. Numeric legacy split folders are reconciled by
copying compatible fragments into a new verified generation; original fragments
are kept. Conflicting legacy folders remain separate choices.

## Validation

- 14 real-device-API instrumentation tests on the dedicated API 34 emulator pass
  for wrapped-key handling, including a fake provider that returns null for the
  constructor probe and a whole retry cycle, then recovers the original
  ciphertext without changing the wrapping public key or metadata.
- Those tests also cover corrupt database preservation, permanent unwrap errors,
  provider retries, raw metadata despite an empty preferences cache, malformed
  metadata, and distinct aliases whose old and new ciphertexts both remain
  readable.
- All 120 database recovery device tests pass. They include interrupted first
  rename and interrupted reverse activation, exact byte restoration after a new
  live wallet exists, cancellation, healed-key cold recheck, database-only key
  retention, and legacy split-folder reconciliation without modifying originals.
- Full integrated production-debug units pass (1,766 tests, 17 intentional skips).
  Test logs are copied to `audit-2026-10-12/android/` in the parent workspace.

## Remaining validation and external blockers

Final translated-resource lint and recovery-screen checks are tracked by at4.
API 26–30 physical provider behavior and exact-store-APK upgrade/signing checks
have not been claimed from the API 34 tests. The attached physical phone is
unauthorized, and the actual Play APK/signing evidence is not available. Those
checks, production identity pins and a signed release build remain release gates.
