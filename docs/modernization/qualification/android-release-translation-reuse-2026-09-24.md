# Android Release translation reuse

The production Release lint handoff in `android-release-missing-translations-2026-09-24.json` lists the remaining untranslated keys. This change fills 131 catalog entries whose text and label role match translations already tracked in this repository. No new translation was authored.

| Destination key | Source key | Source | Catalog entries |
| --- | --- | --- | ---: |
| `network_finalized_history`, `wallet_network_activity` | `common_activity` | Current `common` catalogs | 26 |
| `polkamarkt_action_buy` | `common_buy` | Current `common` catalogs | 10 |
| `polkamarkt_ticket_slippage` | `slippage` | Current `common` catalogs | 8 |
| `wallet_scan_qr` | `commom_scan_qr` | Current `common` catalogs | 6 |
| `wallet_connect_done` | `common_done` | Current `common` catalogs | 21 |
| `wallet_market_refresh` | `common_refresh` | Current `common` catalogs | 9 |
| `polkamarkt_outcome_yes` | `referendum_support_title` | `af7964aeebec2a1ca562c355e1d45027a9649374` | 18 |
| `polkamarkt_outcome_no` | `referendum_unsupport_title` | `af7964aeebec2a1ca562c355e1d45027a9649374` | 16 |
| `wallet_connect_account` | `common_account` | `af7964aeebec2a1ca562c355e1d45027a9649374` | 15 |
| `network_finalized_history`, `wallet_network_activity` (Spanish) | `activity` | `5fb691ea246638ebe70e13c110226274bf466ad3` | 2 |

Each source key's English value equals the destination's English value. All copied XML values are byte-for-byte identical to their source. The historical referendum labels are the same binary Yes/No labels used for prediction-market outcomes; the historical account label is the same generic account label used in the connection review. The historical Spanish Activity label supplies a translation where the current `common_activity` value is English.

English-valued catalog entries were left open for translation, except Spanish `No`, which is the same word in Spanish. The Hindi `slippage` value is English with a trailing escaped newline and was also left open. The historical Chinese `referendum_unsupport_title` values mean “does not have”; they were not reused for a No market outcome. Historical `project_completed` and `project_all` translations were omitted because their plural project-label grammar does not reliably fit a single transfer status or chart time range.

At this first reuse pass (`5991c9eafe52a5c514b9db8ac7b369d9d10d0d63`), XML parsing and source-value comparison passed for all 131 entries. `:app:lintProductionRelease` ran locally with only the signing and production Google OAuth validation tasks excluded because their protected inputs are unavailable. It still failed with 185 `MissingTranslation` errors, 1,021 warnings, and one hint. The lint report's missing locale/key pairs fell from 3,847 to 3,721, resolving 126 language-level gaps; Chinese `zh-rCN` and `zh-rTW` share one language code in that count. The later iOS label reuse is recorded in `android-release-ios-translation-reuse-2026-09-24.md`.

The remaining translation work still needs locale review and a clean protected Release qualification. This reuse does not set a fallback policy or disable `MissingTranslation`.
