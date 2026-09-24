# Isolated encrypted preferences upgrade interoperability

This harness exercises actual library-produced encrypted storage. It is separate from every
production Gradle project and does not change dependency locks, checksum pins, review states,
authority keys or release admission. It uses synthetic values in a new random application ID
and a freshly generated test-only signing certificate. Never use a production package or signer.

Run from the Android repository with Android SDK 36 build tools and cached Kotlin compilers:

```sh
python3 scripts/qualification/legacy-encrypted-preferences/run.py --serial emulator-5554
```

An optional `--work /absolute/new-directory` selects the evidence directory. An already installed
test package rejects before installation. Failed probes remain in their isolated sandbox for
diagnosis; do not reuse that sandbox as a fresh-writer result. The two upgrade installs use `-r`,
without clearing application data. The third APK has a higher version code and the old libraries,
so rollback-format compatibility can be exercised without platform downgrade exceptions.

The old storage graph uses the four adapter 1.0.0-beta01 binaries, DataStore 1.0.0,
security-crypto 1.1.0-alpha06 and Tink 1.8.0. The retained 3.8.6.3 tag pins Kotlin 1.9.24,
coroutines 1.8.1, Gson 2.10.1 and DataStore 1.0.0; publisher POMs record the remaining storage
dependency edges. The old probes are compiled with Kotlin 1.9.24 to avoid introducing newer
stdlib requirements into the historical runtime. This is the storage dependency closure with
those retained version overrides, not a reconstruction of the entire released APK.

The current storage graph uses adapter 1.1.1-beta03, DataStore 1.1.7, Tink 1.13.0 and the current
production runtime lock's support versions. Every current runtime coordinate must actually occur
in `app/gradle.lockfile`. The runner downloads every binary and POM from official Google Maven
or Maven Central on each run, requires parity with cached bytes when present, and enforces frozen
HEAD SHA-256 pins when available. Missing frozen pins are explicitly recorded; publisher parity
does not assert independent dependency approval. DataStore 1.0.0 is verified from Google Maven,
including its real AAR/JAR artifacts, before packaging. Production verification metadata is untouched.

The four phases are:

1. Actual old adapter writes and reopens seven preference types: string, Boolean, Int, Long,
   Float, Double and string set, including Unicode and numeric boundaries.
2. Current adapter reads exact retained values, proves read-only access did not rewrite the
   ciphertext, updates all types, and closes/reopens the persisted store.
3. A separate current process reopens the updated store without rewriting it.
4. An old-library APK reads and reopens that updated store without rewriting it.

The encrypted file stays under `filesDir/datastore` with an unchanged basename because the
adapter authenticates the filename. All later phases assert identical package UID and signer,
Keystore alias inventory and creation dates, and byte-for-byte encrypted-file keyset XML via
SHA-256 comparison. A challenge encrypted with the original non-exportable Android Keystore
master key must still decrypt. This cryptographically checks master-key continuity without
exporting the key. The challenge, values and alias names remain inside the generated sandbox;
logs contain phase outcomes, counts, UID, version, public signer digest and bounded error details.
The app has no Internet permission.

Outputs include publisher artifact/POM hashes and dependency declarations, retained/current
source identities, SDK/device metadata, commands, exact APK hashes and signer digest, per-phase
instrumentation output, and `results.json`. D8 may report pre-existing coroutine local-variable
debug metadata warnings; the real device execution determines interoperability. The test-only
keystore is not release authority and must not be included in a production evidence envelope.

A passing run establishes this local storage-format and key-continuity path on the tested device.
It does not qualify the whole PayWings SDK, real retained user data, production package signing,
all Android versions, encrypted-backup restore, or independently reviewed release qualification.
