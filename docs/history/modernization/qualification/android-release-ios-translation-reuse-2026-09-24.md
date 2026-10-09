# Android Release labels reused from iOS

Source: `sora-ios` revision `7391d1c0e0204b8747444346360b11193edfc1df`, `SoraPassport/SoraLocalizable/<locale>.lproj/Localizable.strings`. The Android destinations are `common/src/main/res/<values directory>/strings.xml` at the Android release candidate. The English text matches exactly: `wallet_receive_xor` / `wallet.receive.xor` is “Receive XOR”; `wallet_status_confirmed` / `wallet.tx.details.completed` is “Completed”.

`wallet_receive_xor` is a receive-sheet title and QR description. `wallet_status_confirmed` labels a confirmed, committed, finalized, or successful transaction. The iOS transaction-detail label serves the same state. The Russian iOS value “Успешно” expresses success, one of the Android confirmed states.

| Android values directory | iOS locale | `wallet_receive_xor` from `wallet.receive.xor` | `wallet_status_confirmed` from `wallet.tx.details.completed` |
| --- | --- | --- | --- |
| `values-b+akk` | `akk` | XOR maḫārum | Gamrum |
| `values-ar` | `ar` | استلام XOR | منجز |
| `values-az` | `az` | XOR qəbul edin | Tamamlandı |
| `values-de` | `de` | XOR empfangen | Abgeschlossen |
| `values-es` | `es` | Recibir XOR | Completada |
| `values-fa` | `fa` | دریافت XOR | تکمیل شده |
| `values-fi` | `fi-FI` | Vastaanota XOR | Valmis |
| `values-fr` | `fr` | Recevoir des XOR | Terminé |
| `values-hi-rIN` | `hi-IN` | XOR प्राप्त करें | पूरा किया गया |
| `values-in` | `id` | Menerima XOR | Selesai¹ |
| `values-iw` | `he` | קבל XOR | הושלם |
| `values-ja` | `ja` | XORを受け取る | 完了しました |
| `values-ms-rMY` | `ms-MY` | Terima XOR | Selesai |
| `values-nb` | `no` | Motta XOR | Fullført |
| `values-nl` | `nl` | Ontvang XOR | Voltooid |
| `values-pt` | `pt` | Receber XOR | Concluído |
| `values-ru` | `ru` | Получить XOR | Успешно |
| `values-sr` | `sr` | Примите XOR | Завршено |
| `values-tr` | `tr` | XOR Alın | Tamamlandı |
| `values-vi` | `vi` | Nhận XOR | Đã hoàn thành |
| `values-zh-rCN` | `zh-Hans` | 收到XOR | 已完成 |
| `values-zh-rTW` | `zh-Hant-TW` | 收到XOR | 已完成 |

¹ iOS `id.lproj` has an English value, `Completed`, for `wallet.tx.details.completed`. Its `project.completed` value is `Selesai`, a generic completed-state label also used for `project.completed.small`. The Android Indonesian status copies that tracked value.

The iOS `no.lproj` values map to Android `values-nb` (Bokmål). `zh-Hans` maps to `values-zh-rCN`; `zh-Hant-TW` maps to `values-zh-rTW`. The existing Android Middle Egyptian values were already present and were not changed. This adds 44 catalog entries across 22 locale files without a fallback policy or `MissingTranslation` suppression.

At Android revision `63b2b20828961c4f3f1344c83777d7df820ff76d`, Android XML parsing and AAPT resource processing passed. Local `:app:lintProductionRelease` ran with only the protected signing and production Google OAuth validation tasks excluded. It reported 183 `MissingTranslation` errors, 1,021 warnings, and one hint, with no other error class. The two requested keys were absent from the lint report; missing locale/key pairs fell from 3,721 to 3,679. The later iOS reuse pass and current JSON handoff are recorded in `android-release-ios-translation-reuse-pass-2-2026-09-24.md`. Full protected Release qualification and review of the remaining translations are still required.
