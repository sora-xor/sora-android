# SORA Wallet Android 3.9.0: release checklist

Human and operations actions for the release. Each task adds its items here.

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
- [ ] Watch the first `Android CI` run of the release branch. Lint could not
      be run on the offline build machine (the lint tool is not in its Gradle
      cache), so this is the first lint result for these changes. Fix any new
      lint errors; update `app/lint-baseline.xml` only when a finding cannot
      be fixed, and say so in the pull request.
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
      `b35dfe16cb3226da4432607288c6287362c5e623532c428f933d552297e9e3e0`
      for both the AAB and the APK.
- [ ] If the run shows the warning "lintProductionDebug failed", read
      `lint-results-productionDebug.html` in the artifact and decide whether
      to fix the findings before uploading.
- [ ] Download the `sora-android-release-<versionName>-<versionCode>` artifact
      (e.g. `sora-android-release-3.9.0-3090001`) and check it
      with `sha256sum -c SHA256SUMS.txt`. Keep `mapping.txt` with the release.
- [ ] Upload the AAB to the Play Console internal track by hand. In the Play
      Console, confirm that the upload key is accepted and that App Signing shows
      the same certificate.

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

### Legal

- [ ] The proprietary SORA Card SDK, PayWings OAuth/KYC and Sumsub IDensic
      AARs were deleted from the tree, but they remain in the public git
      history (for example commit `fccdcf64` and earlier on GitHub). Decide
      whether the licence terms require removing them from history. A
      history rewrite changes every later commit id and needs agreement from
      everyone who works on the repository; it is not part of this release.
