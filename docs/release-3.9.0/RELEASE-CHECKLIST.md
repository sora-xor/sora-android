# SORA Wallet Android 3.9.0: release checklist

Human and operations actions for the release. Each task adds its items here.

Current 12 October 2026 screen/privacy integration and scoped evidence are summarized in
[NEON-INTEGRATION.md](NEON-INTEGRATION.md). Earlier task sections are historical checklists;
local validation does not imply store acceptance or physical-device qualification.

## a1: Retired qualification gates, lean CI

### GitHub secrets and settings

Create a GitHub environment named `production` with required reviewers, and
store these secrets in it. The values come from the Jenkins credentials that
signed 3.8.6.3. Never commit them or paste them into an issue or chat.

| Secret | Value |
|---|---|
| `CI_KEYSTORE_BASE64` | The upload keystore file (Jenkins credential `android_keystore_sora`) as one line of base64, e.g. `base64 < upload.keystore \| tr -d '\n'`. |
| `CI_KEYSTORE_PASS` | Keystore password (Jenkins `android_keystore_storepass_sora`). |
| `CI_KEYSTORE_KEY_ALIAS` | Key alias (Jenkins `android_keyalias_sora`). |
| `CI_KEYSTORE_KEY_PASS` | Key password (Jenkins `android_keypass_sora`). |
| `SORA_GOOGLE_TOKEN_PROD` | The Google Drive backup OAuth client ID used by 3.8.6.3. It must be the same client in the same Google Cloud project, or users will not see their existing Drive backups. |
| `GOOGLE_SERVICES_JSON` | `google-services.json` for `jp.co.soramitsu.sora` as one line of base64. Jenkins decrypts it from `app/google-services.json.gpg`. Without it Crashlytics is off. |

Version code: pass it as the `version_code` input when starting the workflow
(preferred). For tag-triggered runs, set a repository or environment variable
(or secret) `SORA_VERSION_CODE`.

Version name: pass the expected name (for example `3.9.0`) as the
`version_name` input. A tag run takes it from the tag (`android/v3.9.0`
expects `3.9.0`). The run stops before the build if `app/build.gradle.kts`
has another `appVersionName`, and fails after the build if the APK's
`versionName` differs.

- [ ] Create the `production` environment with required reviewers and add the
      six secrets above.
- [ ] Confirm the highest versionCode already uploaded to Google Play for
      `jp.co.soramitsu.sora`, including internal, closed and open testing
      tracks. It is believed to be 122 (3.8.6.3). Use a higher code; the
      recommendation for 3.9.0 is `3090001`.
- [ ] Merge `.github/workflows/android_release.yml` to the default branch
      (`modernize`) so "Android Release" can be started from the Actions tab.
      Until then, push a tag such as `android/v3.9.0` on the release commit:
      tag runs use the workflow file from that commit, take the version code
      from `SORA_VERSION_CODE` and the version name from the tag.
- [ ] Branch protection on `modernize` and `master`: remove the old required
      checks "Modernization Source and Unit Tests", "Explorer Contracts" and
      "Block Explorer Connected Test". Require "Unit tests (productionDebug)"
      (it also checks the release dependency locks) and "Build, lint and 16 KB
      check" from `Android CI`.
- [ ] Watch the first `Android CI` run of the release branch. Local final lint now
      passes (12 October evidence in NEON-INTEGRATION.md), with the existing baseline
      unchanged. CI execution and branch settings still need their own confirmation.
- [ ] Optional cleanup: delete the secrets and variables that only the retired
      qualification workflow read: `ANDROID_DEPENDENCY_SIGNING_REVIEW_*`,
      `ANDROID_MIGRATION_QUALIFICATION_*`, `ANDROID_MIGRATION_CONTROLLER_*`,
      `TAIRA_DEPLOYMENT_*`, `PRODUCTION_ROLLOUT_*`, `PRODUCTION_PI_CONTROLLER_ID`,
      `FUNDED_CANARY_*`, `POLKAMARKT_EXTRINSIC_*`, `POLKAMARKT_IOS_*`,
      `POLKAMARKT_IOS_READ_TOKEN` and the variable `PRODUCTION_VERSION_CODE`.

### Leaked build credential (open security action)

The retired verifier tracked this as `GRADLE_CREDENTIAL_INCIDENT_NOT_CLOSED`
(prior token revoked: no; replacement rotated: no). Nothing else tracks it
now, so it is listed here.

- [ ] Revoke the auth token that was committed in 3.8.6.3 (JitPack). It was
      the `authToken` entry in `gradle.properties` from the first commit
      through 3.8.6.3. The modernization commit removed it from the tree, but
      it is still readable in git history. Rotate its replacement outside
      the repository (a CI secret or the developer's own
      `~/.gradle/gradle.properties`, never a tracked file), and record the
      date and who did it here: `____-__-__`, `________`.

### Jenkins

- [ ] Confirm what the shared `AppPipeline` (`publishType: 'Bundle'`) builds and
      publishes on each branch. It must not upload a productionRelease bundle
      to Google Play; signed Play builds now come from "Android Release".
- [ ] Jenkins now also runs `testProductionDebugUnitTest`. Expect the test stage
      to take longer.

### Build the release

- [ ] Start "Android Release" on the release commit with `version_code`
      (e.g. `3090001`) and `version_name` `3.9.0`. Approve the `production`
      environment.
- [ ] Check the run: every blocking step is green, "Check package name and
      version" shows `versionName='3.9.0'` and the requested versionCode (the
      step fails on any other value), and "Check signing certificate" reports
      upload certificate `26a76e0d3f736f3614dcec04725c439f7165babc8a1e9cb7203a4cdbd233adb1`
      for both the AAB and the locally signed APK. Play's app-signing certificate
      remains `b35dfe16cb3226da4432607288c6287362c5e623532c428f933d552297e9e3e0`;
      Play-delivered APKs use that certificate. The upload-key reset is pending
      until Play confirms eligibility (expected 13 October 2026, 10:21 JST).
- [ ] If the run shows the warning "lintProductionDebug failed", read
      `lint-results-productionDebug.html` in the artifact and decide whether
      to fix the findings before uploading.
- [ ] Download the `sora-android-release-<versionName>-<versionCode>` artifact
      (e.g. `sora-android-release-3.9.0-3090001`) and check it
      with `sha256sum -c SHA256SUMS.txt`. Keep `mapping.txt` with the release.
- [ ] Upload the AAB to the Play Console internal track by hand. In the Play
      Console, confirm that the replacement upload key is accepted and that
      the separate app-signing certificate still matches existing installations.

### Device checks before rollout

- [ ] Install the signed release APK on real arm64 phones (and one 32-bit
      armeabi-v7a phone if available) over an existing 3.8.6.3 install with
      wallets. Check that every account opens with the same address and
      balance, and that a transfer signs. CI only builds and checks the
      artifacts; it does not run the release APK on a device.
- [ ] Optional, on an emulator (never on a phone with real wallets):
      `ANDROID_SERIAL=emulator-5554 bash scripts/run-production-connected-device-qualification.sh`
      runs the wallet-upgrade device tests. The script stops unless the
      device is an emulator, because the tests install and then uninstall
      `jp.co.soramitsu.sora`, which deletes that app's data.

### Rollout

- [ ] Staged rollout in the Play Console (for example 1%, 5%, 25%, 100%),
      watching Crashlytics crash and ANR rates between steps. The retired
      `verifyProductionRollout` gate no longer does this.

## a2: SORA Card, PayWings/KYC and X1 removed

### Credentials shipped in older builds (security)

Every build up to and including 3.8.6.3 compiled these values into
`BuildConfig`, so anyone can read them from a downloaded APK. 3.9.0 no
longer contains them, but the old APKs stay in circulation, so the values
must be treated as public.

- [ ] Ask PayWings / the SORA Card operator to revoke or rotate the KYC
      username and password (`SORA_CARD_KYC_USERNAME_*`,
      `SORA_CARD_KYC_PASSWORD_*`), the SORA Card API key
      (`SORA_CARD_API_KEY_*`), the reCAPTCHA key
      (`SORA_ANDROID_RECAPTCHA_KEY_*`) and the platform id
      (`SORA_ANDROID_PLATFORM_ID_*`), for both TEST and PROD. Record the
      date and who did it here: `____-__-__`, `________`.
- [ ] Delete the secrets that no build reads any more from Jenkins (and any
      CI or developer `local.properties`): `SORA_CARD_*`, `X1_ENDPOINT_URL_*`,
      `X1_WIDGET_ID_*`, `SORA_BACKEND_*`, `SORA_ANDROID_RECAPTCHA_KEY_*`,
      `SORA_ANDROID_PLATFORM_ID_*`. Keep `SORA_GOOGLE_TOKEN_*` (Google Drive
      backup).

### Google Play Console

- [ ] Data safety form: the app no longer includes the PayWings / Sumsub
      KYC SDKs. Remove the data types that only they collected (for
      example identity documents and photos, face/biometric data for
      liveness, precise and approximate location, audio, and the name,
      email, phone and address used for card sign-up) and the SORA Card
      purposes. Check the final form against the 3.9.0 build before
      publishing it.
- [ ] Permission declarations: 3.9.0 no longer requests
      `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `RECORD_AUDIO`,
      `NFC`, `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE`,
      `WRITE_EXTERNAL_STORAGE`, `WRITE_SETTINGS` or
      `READ_ASSISTANT_APP_SEARCH_DATA`. Remove any declaration for them (for
      example the photo and video permissions declaration). Before
      submitting, confirm the permission list that Play Console shows for
      the uploaded AAB.
- [ ] Store listing, screenshots, feature graphic and privacy policy: remove
      mentions of SORA Card, IBAN, "Buy XOR with fiat" and KYC.
- [ ] Release notes: tell card holders that SORA Card has moved out of the
      wallet. Draft (confirm the wording and where card holders should go
      with the SORA Card team first):
      - EN: "SORA Card is no longer part of SORA Wallet. Your wallet,
        recovery phrase and balances are not affected. To manage your card,
        use the SORA Card app or contact SORA Card support."
      - JA: 「SORA Wallet から SORA Card の機能を削除しました。ウォレット、
        リカバリーフレーズ、残高に影響はありません。カードの管理は
        SORA Card アプリ、または SORA Card サポートをご利用ください。」

### Device check before rollout

- [ ] On a phone with Play 3.8.6.3 and a wallet, where the SORA Card banner
      was visible (and, if possible, one that was signed in to SORA Card),
      install the 3.9.0 release build over it. Check: the app opens with the
      same accounts and addresses; the wallet hub, More and XOR asset
      details show no SORA Card or "Buy" items; Edit view of the hub lists
      no card items; a swap completes and returns to the wallet; the
      recovery phrase can still be shown after PIN.

### PI indexer (backend team)

- [ ] Android 3.9.0 no longer asks the indexer for `mobileConfig.soracard`
      (Android 3.8.6.3 did not use the indexer's `mobileConfig` at all).
      Before removing `soracard` from the indexer schema, confirm that no
      shipped iOS build asks for it; until then keep it (always `false`).

### Legal

- [ ] The proprietary SORA Card SDK, PayWings OAuth/KYC and Sumsub IDensic
      AARs were deleted from the tree, but they remain in the public git
      history on GitHub. They were added in commit `6201e7cc` and are still
      at the tip of the default branch `modernize` (`203d231a`) and of 25
      other pushed branches, for example `c01b3ba2` on
      `codex/android-readiness-20260924` and
      `recovery/20261009/sora-android-release-candidate-20260924` (checked
      against the local remote-tracking refs on 2026-10-10). Decide
      whether the licence terms require removing them from history. A
      history rewrite changes every later commit id and needs agreement from
      everyone who works on the repository; it is not part of this release.

## a3: Live-runtime signing, indexer tolerance, flags, version

### Build inputs (now enforced by Gradle)

A `productionRelease` build stops before compiling, with a list of what is
missing, unless all of these are present:

- [ ] `SORA_VERSION_CODE` (the "Android Release" workflow sets it from the
      `version_code` input or the `SORA_VERSION_CODE` variable). For 3.9.0 it
      must be within `3090000..3090099` and above 122; use `3090001` for the
      first upload and `3090002`, `3090003`, … for re-uploads. `CI_BUILD_ID`
      is no longer used for Play builds.
- [ ] `SORA_GOOGLE_TOKEN_PROD`: the same Google Drive backup OAuth client that
      3.8.6.3 used (same Google Cloud project). A different client builds
      fine but hides every existing Drive backup from users.
- [ ] `app/google-services.json` for `jp.co.soramitsu.sora` (the workflow
      writes it from `GOOGLE_SERVICES_JSON`); without it Crashlytics is off.
- [ ] Signing credentials (unchanged).

### Before each release candidate (mainnet changes often)

SORA mainnet upgraded its runtime three times in 19 days. The app now signs
for whatever runtime the chain runs, but check it against the live chain for
every candidate:

- [ ] On an emulator (never a phone with real wallets), run the zero-cost live
      signing test. Both tests must pass (signing ends with "Inability to pay
      some fees"; the second reads a 20,000-block-old block through the
      status archive node):
      `ANDROID_SERIAL=emulator-5580 ./gradlew :sorasubstrate:connectedProductionDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.soraLiveNetwork=true -Pandroid.testInstrumentationRunnerArguments.class=jp.co.soramitsu.sora.substrate.live.MainnetLiveSigningTest`
      (`adb logcat -s SoraLiveSigning` shows the runtime version, fee and the
      node's answer). Last passed on 2026-10-10 at 17:31 JST against spec 133 /
      tx 131.
- [ ] Run the live indexer contract test:
      `RUN_LIVE_PI_CLIENT_TESTS=true ./gradlew :feature_blockexplorer_api:testProductionDebugUnitTest --tests '*PolkaswapIndexerClientLiveContractTest*' --rerun`.
      Keep `--rerun`: Gradle does not see the environment variable as an
      input. Without it, an up-to-date test task is skipped and nothing runs
      against the indexer. Check that the report lists 4 tests and 0 skipped.
      Since round 4 it uses the production transport (strict JSON, size
      limit, checkpoint checks, file cache) and checks history rows against
      mainnet block hashes, so a pass also means the app decodes the real
      responses. Last passed on 2026-10-10 at 18:13 JST.
- [ ] On a real phone with 3.8.6.3 and a funded wallet, install the release
      build over it and send a small XOR transfer and a small swap: the fee is
      shown, both finalize, and Activity lists them.
- [ ] On a test device, with a team test wallet that has more than 100 signed
      operations (never a customer's recovery phrase), open Activity, make a small
      transfer, wait a minute, then scroll Activity and a token's history to
      the end. Expect no crash and no transaction listed twice (round-3
      review fix for "load more"). The rows must actually appear: if Activity
      says "Activity is temporarily unavailable" for this wallet, the shipped
      app rejects the indexer's real history responses; stop the rollout.
      (The live contract test covers the same decoding on a public account;
      this step covers the screen.)

### PI indexer (backend team)

- [ ] The indexer was upgraded on 2026-10-10 and now reports `nexusAvailable`,
      `polkamarktVisible` and `tairaDefaultVisible` as true (its built-in
      defaults). 3.9.0 production ignores these three (they are off in the
      store build), but develop/Soralution builds follow them. Set
      `POLKASWAP_MOBILE_CONFIG_*` deliberately for production rather than
      relying on the defaults, because a later app release may honour them.
- [ ] Ask the indexer team for an account-history plan that covers received
      transfers (for example a timestamp-ordered plan on `dataTo`, or an
      account-participant index). Until then Activity lists only operations
      the account signed; incoming transfers are missing (balances are
      correct). The app says so on Activity and asset history ("Transfers
      you received may not be listed here yet. Your balance is not
      affected."). When the plan ships, change `accountHistoryFilter` and
      `PiHistoryCoverage.LISTS_RECEIVED_TRANSFERS` together. Consider the same
      line in the release notes.
- [ ] Keep the indexer's filter-array limit (100) and the history `calls`
      shape stable, or tell the mobile team before changing them.

### SORA nodes (ops)

- [ ] Keep `mof2.sora.org` an archive node with HTTPS JSON-RPC (it is in the
      default node list). The app asks it for System events, metadata and
      runtime version at exact old block hashes when `ws.mof.sora.org` has
      discarded that state (it keeps about 256 blocks), so pending
      transactions older than ~25 minutes can still be resolved. If mof2
      stops serving old state, those transactions stay "pending" in the app
      (nothing is lost; deleting that wallet and its legacy claim wait). Tell
      the mobile team before moving or pruning it.
- [ ] `ws.mof.sora.org` (HTTPS) is the only endpoint for fee quotes, nonces,
      runtime reads and submission. While it is down, nobody can send, even
      though balances still load from mof2/mof3. Monitor it as a release
      dependency. Send and Swap then show "The network fee can't be checked
      because the SORA network can't be reached right now…" with Try again
      (round 4); support can point users to that message.

### Product decisions to confirm

- [ ] Sending no longer depends on the node selected in More > Nodes (fees,
      runtime, nonce and submission all use the reviewed
      `https://ws.mof.sora.org`). Decide whether a second reviewed signing
      endpoint is wanted for outages of that node; that is a code change in
      `Sora2RuntimeContract` and needs a security review.
- [ ] Accept, or fix before rollout, a regression for one group of upgrading
      users: 3.8.6.3 signed and sent over the WebSocket of the selected node,
      so a user whose network cannot reach `ws.mof.sora.org` (regional or
      corporate block) but who used mof2, mof3 or a custom node could send
      on 3.8.6.3. After upgrading to 3.9.0 their balances still load, but
      every fee quote and send fails, and changing the node does not help.
      Keys and funds are not affected and nothing latches; the screens say
      the SORA network can't be reached and offer Try again. The fix is the
      second reviewed signing endpoint above (mof2 is already an HTTPS
      archive endpoint for status reads). If accepted, mention it in the
      release notes and the support FAQ.
- [ ] Taira, the Nexus (Minamoto) portfolio, Polkamarkt and IrohaConnect are
      off in the production app regardless of remote configuration. Turning
      any of them on for production is a code change
      (`ReleaseSurfacePolicy`) and, for Polkamarkt, a Google Play
      prediction-market policy review; for IrohaConnect, a security review.

### Translations (round 4)

- [ ] New strings `network_fee_unreachable`, `network_fee_unavailable`,
      `network_fee_unavailable_short` and `common_try_again` are in English
      and Japanese; the other locales carry the English text (no lint
      baseline change). Send them with the other 3.9.0 strings for
      translation. The changed `activity_received_transfers_note` ("Your
      balance is not affected.") needs the same.

## a4: Key safety and lockout fixes (core)

### Before each release candidate

- [ ] On an emulator (never a phone with real wallets), run
      `bash scripts/run-production-connected-device-qualification.sh` with
      `ANDROID_SERIAL` set to the emulator. It now includes
      `WalletUpgradeScenarioTest` (3.8.6.3 and 2.3.3 storage upgraded through
      the production path; 16 scenarios, each ends with a signature). Use an
      emulator with 4 KB pages (for example SORA-Api34): on 16 KB-page
      emulators the MigrationManager qualification step cannot load
      mockk-android's test agent.

### Real-device upgrade QA (test phones and team test wallets only)

The device tests write storage with the released apps' own code on an
emulator. Confirm on real phones, installing the release build over Google
Play 3.8.6.3:

- [ ] Clean multi-account wallet (a 12-word, a 24-word and, if available, a
      15-word account): all addresses unchanged, a small transfer signs.
- [ ] Abandoned create: in 3.8.6.3 start "Create account", enter a name, tap
      Continue, then back out; upgrade. Expect the app to open (existing
      wallet unchanged, or onboarding when it was the first account), and
      importing that phrase to work.
- [ ] Abandoned add-account on a phone that already has a wallet: the
      existing wallet opens and signs.
- [ ] A phone that came from 2.x and deleted its original account in 2.4+:
      opens; current accounts sign.
- [ ] Low storage (fill the phone's storage almost completely before the
      first start): the upgrade either continues with a keys-only backup or
      stops with the storage message and the amount needed; after freeing
      space, Try again continues.
- [ ] Kill the app (swipe away) during the first start after the upgrade,
      several times: it resumes and the wallet signs.
- [ ] Device-to-device transfer (Pixel "Copy apps & data") of a 3.8.6.3
      install to a new phone, then the release build: note what is shown.
      The Keystore alias does not transfer, so the keys cannot be decrypted;
      the person must restore with their phrase. Until the Safe Mode task
      lands the recovery panel offers Try again and export only. Record the
      result for the Safe Mode task.
- [ ] Import a 15, 18 or 21-word SORA2 phrase from a team test wallet: the
      address matches 3.8.6.3.

### Follow-up work required before release

- [ ] Safe Mode screens (fix map F11 and §4) must land: "Restore with
      phrase", phrase reveal after PIN, adoption of set-aside key sets,
      "Free up some space" with Check again, live network in read-only
      mode, and a support action that cannot crash without an email app.
      This task only exposes the flags (`WalletUpgradeBackup.recoveryOptions()`,
      `quarantine()`, `UserRepository.adoptRetainedWallet()`,
      `unavailableWalletIds()`).
- [ ] Accounts shown as read-only (`UNAVAILABLE`) need a visible label and an
      explanation in the account list (Safe Mode task).

### Product decisions to confirm

- [ ] Automatic retry of an interrupted wallet check: 3 times in a row, then
      the options are shown (Try again always works).
- [ ] Backup pruning: keep the newest 3 verified backups and every backup
      taken before a schema migration.
- [ ] Keys-only backup when storage is low (encrypted preferences, wrapped
      key and account list; no database copy).
- [ ] New accounts are kept in memory until the create flow finishes; the
      Google Drive backup made during creation may therefore exist for an
      account the person then abandoned on the phone (it can be restored
      from Drive).
- [ ] An account whose keys are incomplete or unreadable is shown read-only
      instead of blocking the app.

### Support

- [ ] New message for support: "This wallet can't be added here" (an import
      whose address already holds different keys; nothing was changed).
      Prepare an answer: the person's funds are safe; ask for the recovery
      export, do not ask them to reinstall.
- [ ] Set-aside files are never deleted automatically and count towards the
      app's storage: `no_backup/wallet-backup-quarantine-*`,
      `no_backup/wallet-quarantine-*` and
      `no_backup/wallet-corrupt-quarantine-*`. A later release may offer a
      confirmed cleanup.

### a4 review fixes: actions

Before each release candidate:

- [ ] Same emulator run as above; `WalletUpgradeScenarioTest` now has 26
      scenarios (adds read-only repair and removal, phone transfer without
      the Keystore key, the 2.4.0 layout, blank rows, clock skew and a busy
      Keystore during the key unwrap). Also run the ordered
      `EncryptedWalletMigrationStorageTest` phases with
      `scripts/run-encrypted-wallet-upgrade-qualification.sh` (they depend on
      process restarts between phases and fail when run all at once).

Real-device QA (test phones and team test wallets only):

- [ ] Partial key set: on 3.8.6.3, force-stop the app during "Import" right
      after Continue (repeat until an account shows without working keys, or
      use the emulator scenario as reference). After the upgrade that account
      is read-only; importing its phrase again makes it sign; other accounts
      unaffected.
- [ ] Device-to-device transfer (Pixel "Copy apps & data") of a 3.8.6.3
      install, then the release build: the recovery panel must appear (not
      onboarding that always fails); record what it offers. Once the Safe
      Mode task wires "Set up this device again", confirm that after it the
      app starts as new, the phrase restores the same address and the old
      files are under `no_backup/wallet-quarantine-*-set-up-again/`.
- [ ] Kill the app during the first start after an upgrade, then let a
      background job start the app (for example wait for a scheduled
      worker or reboot) before opening it: opening the app must still retry
      the verification automatically.

Follow-up (Safe Mode task, required before release):

- [ ] Wire `WalletUpgradeBackup.requestSetUpAgain()` to a "Set up this
      device again (keeps old data)" action with a typed confirmation, then
      restart the app process (start the launcher activity with
      `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` and end the
      process). Offer it when `recoveryOptions().canSetUpAgain`; when
      `restoreNeedsSetUpAgain` is true, "Restore with recovery phrase" and
      "Restore from Google Drive" go through it. (Round 2: not offered for
      transient or storage states; see `setUpAgainScope` below.)
- [ ] For each read-only account, show `UserRepository.unavailableWalletOptions()`:
      "Import this account's phrase" when `canRepairWithPhrase`, otherwise
      "Remove this account, then import its phrase" (`canRemoveAndImport`),
      and the phrase reveal when `canShowPhraseAfterPin`.
- [ ] Show the onboarding error `WALLET_KEYS_UNAVAILABLE` with a direct link
      to the recovery options.

Product decisions to confirm:

- [ ] "Set up this device again" renames (never deletes) files into
      `no_backup`: only the database for database and verification
      problems, and also the preference files and wrapped key when this
      phone cannot use them (round 2). They stay on the phone until a later
      release offers a confirmed cleanup.
- [ ] Removing a read-only account keeps a copy of the encrypted preference
      files in `no_backup/wallet-quarantine-*-removed-unavailable-account/`
      first; without that copy (for example a full disk) the removal is
      refused.
- [ ] Old current-schema backup snapshots are removed (not only set aside)
      when the newest three pass full validation and every wallet in the
      snapshot is a live account whose keys verified on this phone.

Support:

- [ ] New messages: "This account can't be repaired as it is" (remove the
      account and import its phrase again; funds are safe) and "This phone
      can't unlock the saved wallet data" (typically a phone-to-phone
      transfer; restore with the recovery phrase; never ask them to
      reinstall or clear data).

### a4 review fixes, round 2: actions

Before each release candidate:

- [ ] Same emulator run; `WalletUpgradeScenarioTest` now has 30 scenarios.
      It adds: set up again after a damaged database, the 2.4.0 account with
      an undecryptable copy, release-era keys of another address, and a
      retry whose counter cannot be saved.

Real-device QA (test phones and team test wallets only):

- [ ] Device-to-device transfer (Pixel "Copy apps & data") of a 3.8.6.3
      install with two accounts, then the release build. The recovery panel
      must not show "Continue read-only", and no button may crash the app.
      Try again and Contact support must work.
- [ ] On a nearly full phone, kill the app during the first start after
      the upgrade. The next start must show the recovery options or open
      the wallet, never crash repeatedly.

Follow-up (Safe Mode task, required before release):

- [ ] Read `recoveryOptions().setUpAgainScope` before confirming "Set up
      this device again":
      - `DATABASE_ONLY`: every stored key set stays on the phone. After the
        restart, offer "Restore wallets found on this phone"
        (`WalletUpgradeBackup.quarantine().retainedWalletIds` with
        `UserRepository.adoptRetainedWallet`) next to the phrase and Drive
        restore.
      - `ALL_WALLET_FILES`: the app cannot read the old keys afterwards.
        When `canShowPhraseAfterPin` is true, offer "Show my recovery
        phrase" first, and ask the person to confirm that every phrase is
        written down.
      - Never offer set-up-again or restore when the scope is `NONE`
        (transient or storage states).
- [ ] Per read-only account (`unavailableWalletOptions()`):
      - When `showPhraseBeforeRemoval` is true, show the phrase (after PIN)
        and ask for confirmation before "Remove this account".
      - When `hasReleaseEraCopy` is true, tell the person that removing the
        account keeps its older keys, so it can be restored from the phone
        afterwards.

Product decisions to confirm:

- [ ] Set-up-again moves key material only when this phone cannot use it
      (missing Keystore key, unreadable preference files). Every other
      recovery state sets aside the database only.
- [ ] Removing an account removes the release-era (2.x) keys only when they
      are proven to be that verified account's own. Otherwise they stay on
      the phone, and the account can be restored from there.

### a4 retry: actions

Before each release candidate:

- [ ] Same emulator run. `WalletUpgradeScenarioTest` now has 34 scenarios.
      It adds SORA v1 1.6.1, the 2021 SORA2 builds 2.1.0 and 3.0.0 (plain
      SharedPreferences) and a new install importing 12, 15, 18, 21 and
      24-word phrases. `core_db` adds the startup check
      (`startupWithNoPendingMigrationHashesCopiesAndValidatesNothing`).

Real-device QA (team test phones and team test wallets only):

- [ ] If the team still has an APK of a 2021 SORA build (2.1.0 or 3.0.0),
      install it on a test phone and restore a team test wallet. Then
      install the release build over it. Expect the same address and a
      small transfer that signs. Do the same with a SORA v1 APK if one
      exists. Expect the app to open at onboarding, and the same phrase to
      restore the SORA2 account. Without such APKs, the emulator scenarios
      (written with those builds' own storage code) are the evidence;
      record that here.
- [ ] Cold start on a low-end test phone that has the 3.8.6.3 wallet
      upgraded (second start and later): no visible delay before the splash
      compared with 3.8.6.3. Check Android vitals "slow cold start" after
      the staged rollout starts.

Engineering decision to confirm:

- [ ] 2021 builds stored keys in `sora_prefs.xml`. As in every release since
      2.3.2, the DataStore's `SharedPreferencesMigration` copies those
      values unchanged into the DataStore on its first read, then removes
      the XML file. 3.9.0 keeps this. The verified pre-migration backup
      (always made, because those databases need a schema migration) holds
      the original file byte for byte. The engineering owner confirms that
      this is acceptable under the "never move key material" rule.

## a5: SORA Neon UI foundation (Android)

### Design and product sign-off

- [ ] Design reviews the light and dark screenshots
      (`scratchpad/screens/android/a5-ui-foundation/`) and the debug parity
      screen (More → Debug Menu → "SORA Neon components") on a real phone.
      Calibrate the single blur factor `DESIGN_BLUR_FACTOR` (0.87) against an
      iOS screenshot if glows look too tight.
- [ ] Support and product approve the new recovery panel text, which no
      longer mentions reinstalling or clearing data: "SORA paused the upgrade
      because it could not check every existing account yet. Nothing was
      deleted or replaced, and your funds stay safe on the blockchain. Try
      again, or contact support for help."
- [x] New installs default to dark (spec principle 9). Existing data/preferences
      preserve the former default; explicit theme choices still win. Device regression
      passed. See [NEON-INTEGRATION.md](NEON-INTEGRATION.md).
- [ ] Product decision: the Android 12+ system starting window follows the
      system theme, so a person who chose dark inside the app but uses a
      light phone sees a light launch window for a moment (the splash
      activity after it is dark). Fixing it needs
      `UiModeManager.setApplicationNightMode` on API 31+, which changes how
      "System appearance" is detected; decide in the splash task.

### QA before release (real devices)

- [ ] Body text grew by 1 sp. Check fixed-height rows for clipping at font
      scale 1.0, 1.3 and 2.0, and in ja, ar and hi.
- [ ] Light and dark on API 26/27 (MINIMAL tier), API 28–30 (REDUCED) and
      API 31+ (FULL); turn on battery saver, "Remove animations" and the new
      "Reduce effects" switch, and confirm nothing animates on idle screens.
- [ ] Dialogs (PIN errors, invite dialogs, node alerts) show the sheet colour,
      rounded corners and cyan actions in both themes.
- [ ] QR scanner: title clear of the status bar, brackets visible, scanning
      works with the darker 55% scrim.
- [x] Regenerate the baseline profile after screen integration; emulator collection
      and exact generated profile hashes are recorded in NEON-INTEGRATION.md.

### Translations

- [x] Translate `appearance_reduce_effects`,
      `appearance_reduce_effects_description` and `common_loading` in all ten production
      languages. Native review remains pending; non-shipping/test locale fallback is explicit.
- [ ] `scripts/verify-middle-egyptian-localization.mjs` (manual) already
      failed before this task on the a4 English placeholders; it now also
      lists these three keys and `wallet_upgrade_recovery_message`. Middle
      Egyptian is a test-only locale and not offered in production.

### a5 review fixes: actions

- [ ] Support and product approve the recovery panel callout ("Please
      don't uninstall SORA or clear its storage. Your keys are stored on
      this device."), the "Working…" / "Still working…" lines and the
      "Show technical details" toggle. The callout's "Your keys are stored
      on this device" needs the engineering sign-off of spec §9.1-2. Note
      for the reviewer: the panel appears only after the upgrade stopped
      without changing wallet data, and nothing on this path deletes key
      material, so the (possibly unreadable) keys are still on the device
      whenever it shows.
- [ ] Support: the upgrade-recovery email now arrives with "Recovery code:
      …" in its body. The code is also under "Show technical details" on
      the panel.
- [x] Recovery panel translations implemented for every offered production
      language (English plus ru/es/fr/de/nb/zh-CN/in/tr). Japanese resources
      remain available for QA. See `fixes/recovery-localization.md` and its
      checked string inventory; no production recovery key relies on English.
- [ ] Native-language review of those translations and every new preserved-data
      choice. Automated placeholder/XML checks and the nine-language U6
      screenshot review do not establish linguistic accuracy.
- [ ] QA (real device, light theme): open Scan on Android 14 and on 15+;
      the clock and icons are white over the camera and no light strip
      shows above the scanner.
- [ ] QA (real device, dark theme): send a small test transfer and check
      that the "Transaction submitted" toast is readable.
- [ ] QA: on the recovery panel (team test phone with a forced recovery
      state), tap Retry and Export and check the "Working…" state, the
      greyed other actions and the "Still working…" line after 10 s.
- [ ] QA with Settings → Accessibility → Contrast raised (Android 14+):
      captions use the secondary text colour.

## a6: Safe Mode recovery and upgrade screens

### Sign-off (spec §9.1-2: every assurance needs an engineering owner)

- [ ] Engineering owner signs off the assurance strings marked "verified:"
      in `common/src/main/res/values/strings.xml` (`safe_mode_*`) and
      `app/src/main/res/values/strings.xml` (`recovery_*`). Each note names
      the code that makes the sentence true for its state.
- [ ] Support and product approve the U6 copy, the Export sheet (it is a
      file for support, not a backup), and the Contact support details
      (code, app version, time, Android version; no addresses).
- [ ] Support: confirm `support@sora.org` is the right address and whether
      an official support web page should be added as a fallback (fix map
      F11.2 open question).

### Translations (release blocker per spec §9.1-6)

- [x] Translate the scoped Safe Mode/recovery/account/transaction messages
      into all eight non-English production languages. The actual production
      picker and locale configuration define the shipping set; ar/az/fa/fi/hi/
      iw/ms/nl/pt/sr/vi/zh-TW and historical test locales retain the documented
      test-only English fallback policy, rather than being claimed as translated.
- [ ] Native review and full state/large-font/RTL QA beyond the captured U6 screen.

### QA (team test phones only, never a phone with real funds)

- [ ] Damaged database (debug build): `adb shell run-as jp.co.soramitsu.sora`
      and replace `databases/app.db` with any text, then open the app.
      Expect U6 with `DATABASE_CORRUPT` under Technical details. Check Try
      again, Export (share sheet), Contact support with and without an email
      app, and "Restore with recovery phrase" (restarts into the welcome
      screen; the old database is in `no_backup/wallet-quarantine-*`).
- [ ] With a wallet that has a PIN, repeat the step above and use Show my
      recovery phrase: wrong PIN refused, three wrong PINs lock for a
      minute, the phrase stays veiled until tapped, a screenshot or the
      recents screen shows nothing, and going home drops it.
- [ ] After "Restore with recovery phrase", import a different phrase, then
      More → Login & Security shows "Wallets found on this phone"; restore
      it and check the address in Crypto Accounts.
- [ ] Low storage (fill the phone before upgrading from 3.8.6.3 with a
      database below the current schema): "Free up some space" shows the MB
      needed, Open storage settings opens Settings, and coming back checks
      again by itself.
- [ ] Slow upgrade (a phone with many accounts, or an API 26 device): the
      progress screen appears after 1.5 s, "Still working…" after 10 s,
      Keep waiting / Get help after 60 s; Back does nothing while it runs.
- [ ] 4-digit PIN from an old install: unlock shows "Choose a new 6-digit
      PIN", then the 6-dot setup; afterwards "Welcome to the new SORA
      Wallet" appears once.
- [ ] Offline unlock (airplane mode): a 12/24-word wallet opens directly; an
      unchecked 15-word v1 wallet shows "Waiting for network" with Change
      node and continues when the network returns.
- [ ] Read-only mode (Continue in read-only mode from U6): the home shows the
      banner; Send / Swap are disabled with the reason; Try upgrade again
      runs the check with the progress screen; balances refresh (the node
      connection starts).
- [ ] TalkBack on the U6 panel, the progress screen (live "Still working"
      line), the PIN dots ("2 of 6 digits entered") and the phrase grid.
- [ ] Font scale 200% on U4, U6 and the phrase grid: no clipping, the panel
      scrolls.

### Follow-ups

- [x] A per-account read-only label, reason and recovery route on account list/home.
      Covered by ReadOnlyAccountScreensTest and ReadOnlyHostFragmentTest; signing stays gated.
- [x] Fix `WalletUxAcceptanceTest` (stale reflection on
      `OnboardingViewModel.tempAccount`, removed in a4). The actual create/PIN/wallet/Swap
      journey passes in the clean device suite recorded in NEON-INTEGRATION.md.
- [x] Regenerate the baseline profile after screen integration (see NEON-INTEGRATION.md).

### a6 review fixes (round 1)

- [ ] Engineering owner signs off the new "verified:" strings in
      `app/src/main/res/values/strings.xml`: `recovery_restore_pending_body`,
      `recovery_sheet_*`, `recovery_phrase_unchecked`,
      `recovery_phrase_unreadable` and the extended
      `recovery_restore_sheet_keys_kept` (other wallets are listed in Login &
      Security after a database-only restore).
- [x] The additional recovery-result and pending-restore messages above are
      included in the production translation inventory. Native review remains
      open under the common translation gate.
- [ ] QA (team test phone, debug build): force a wallet-check failure after
      startup by making the VERIFIED journal finish before it started
      (`run-as jp.co.soramitsu.sora sqlite3 databases/app.db "update
      walletMigrationJournal set completedAt = startedAt - 1000"`, as after
      a clock change). The panel shows VERIFIED_RECEIPT_INVALID and offers
      Restore with recovery phrase, Show my recovery phrase, read-only and
      Export; "Restart and restore" restarts into the welcome screen with
      the database in `no_backup/wallet-quarantine-*-set-up-again/`.
- [ ] QA: with a restore requested but not finished (fill the phone so the
      set-aside cannot create its folder, then restart), the panel says
      "Finishing the restore you started", does not retry by itself, and
      "Restart and continue" restarts the app; after freeing space the
      restore finishes.
- [ ] QA: on a 3-account phone, restore one wallet with its phrase, then
      Login & Security → "Wallets found on this phone" restores the others
      without changing the selected account.
- [ ] QA (RTL, Arabic or Hebrew system language): the PIN screen's
      "Choose a new 6-digit PIN" panel and the account check follow RTL;
      the keypad keeps its order.

## Integrated recovery review — 12 October 2026

- [x] Independent review fixes committed as `10e8ef51`: database-only generations
      are returnable without moving keys/PIN; damaged siblings no longer hide
      intact return choices. New choice text translations: `ac1a5f9f`.
- [x] All 123 preserved-generation instrumentation tests passed on isolated
      API 34; all 1,766 JVM tests passed in each of DevelopDebug and ProductionDebug
      (17 intentional skips each), and both debug APK variants assembled.
- [ ] Obtain and verify original store-signed APKs before claiming exact old-app
      upgrade continuity. The denied historical download was not bypassed.
- [ ] Authorize the team physical phone and run API 26–30 provider/recovery QA.
- [ ] Verify replacement Play upload-key eligibility after 13 October 2026,
      10:21 JST; verify actual Play signing certificate and Google identity pins.
- [ ] Build and inspect the signed release artifact with the verified upload key
      and production Google identity. Local preparation does not require waiting
      for Play's upload-key activation; publication still requires acceptance and
      the applicable store/device checks. No upload was made by the audit pass.
