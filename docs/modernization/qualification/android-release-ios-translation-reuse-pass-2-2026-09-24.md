# Android Release: second iOS translation reuse pass

Android starting revision: `63b2b20828961c4f3f1344c83777d7df820ff76d`. Immutable iOS source revision: `d58b993678f2e2183102f3047a05bd18c127c27f`, `SoraPassport/SoraLocalizable/<locale>.lproj/Localizable.strings`. Android destinations are in `common/src/main/res/<values directory>/strings.xml`. Every source key's English value exactly equals its Android destination English value. The copied locale values differ from English and are byte-for-byte equal to the iOS values.

| Android destination key | iOS source key | UI meaning | Catalog entries |
| --- | --- | --- | ---: |
| `network_finalized_history`, `wallet_network_activity` | `tabbar.activity.title` | Wallet activity navigation and history | 15 |
| `wallet_connect_account` | `common.account` | Account label in connection review | 6 |
| `wallet_scan_qr` | `commom.scan.qr` | QR scanning action | 1 |
| `wallet_connect_done` | `common.done` | Completion button | 1 |
| `wallet_market_refresh` | `common.refresh` | Refresh action | 1 |

The locale mapping and exact reused values are:

| Android directory | iOS locale | Destination key(s) and value |
| --- | --- | --- |
| `values-b+akk` | `akk` | `wallet_network_activity` = Epšētum; `wallet_connect_account` = Nikkassum; `wallet_scan_qr` = Kunukkam QR šasûm; `wallet_connect_done` = Gamrum; `wallet_market_refresh` = Edēšum |
| `values-de` | `de` | `network_finalized_history`, `wallet_network_activity` = Aktivität; `wallet_connect_account` = Konto |
| `values-fa` | `fa` | `network_finalized_history`, `wallet_network_activity` = فعالیت |
| `values-fi` | `fi-FI` | `network_finalized_history`, `wallet_network_activity` = Toiminta; `wallet_connect_account` = Tili |
| `values-hi-rIN` | `hi-IN` | `wallet_connect_account` = खाता |
| `values-in` | `id` | `network_finalized_history`, `wallet_network_activity` = Aktivitas |
| `values-ja` | `ja` | `wallet_connect_account` = アカウント |
| `values-nb` | `no` | `network_finalized_history`, `wallet_network_activity` = Aktivitet; `wallet_connect_account` = Konto |
| `values-nl` | `nl` | `network_finalized_history`, `wallet_network_activity` = Activiteit |
| `values-tr` | `tr` | `network_finalized_history`, `wallet_network_activity` = Aktivite |

This pass adds 24 catalog entries in 10 files. `network_finalized_history` and `wallet_network_activity` use the iOS Activity navigation label, which serves the same wallet section. The Akkadian `Scan QR code`, `Done`, and `Refresh` translations also agree with values already tracked under the corresponding Android `common` keys. The Android `wallet_chart_range_all` remains open: the iOS `project.all` text labels a project filter, and the prior Android handoff determined its plural grammar is uncertain for a chart time range. No translation was invented, no English fallback policy was added, and `MissingTranslation` remains enabled.

All modified XML catalog files parse, and a source-value check compared all 24 added entries to the iOS source at the pinned revision. The local resource and lint command was:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/Users/takemiyamakoto/Library/Android/sdk \
./gradlew :app:processProductionReleaseResources :app:lintProductionRelease \
  --exclude-task :app:verifyProductionReleaseSigning \
  --exclude-task :common:validateProductionCloudBackupConfiguration \
  --offline --console=plain --continue
```

`:app:processProductionReleaseResources` passed. Lint reached the report stage and failed on the remaining 180 `MissingTranslation` errors; it also reported 1,021 warnings and one hint, with no other error class. The lint handoff JSON was regenerated against that report: 3,679 to 3,655 missing locale/key pairs, resolving exactly 24. The fully protected Release build still requires signing and production Google backup credentials, followed by review of the remaining translations.
