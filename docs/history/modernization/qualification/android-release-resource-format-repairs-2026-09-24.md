# Android Release resource format repairs — 2026-09-24

Starting Android revision: `13abd5765452aad11620794c9db7a674cad834d4`.
The same-language wording below comes from the checked-in iOS catalog at
`2f5386585fd19f8762b87fa68e364f8c3646e4f8`,
`SoraPassport/SoraLocalizable/<locale>.lproj/Localizable.strings`.
The Android argument is a Java/Kotlin `%s`; the equivalent iOS argument is `%@`.

| Android resource and locale | iOS source key | Android value after repair |
| --- | --- | --- |
| `polkaswap_insufficient_balance`, `values-az` | `polkaswap.insufficient.balance` | `Balans kifayət deyil (%s)` |
| `polkaswap_insufficient_balance`, `values-sr` | `polkaswap.insufficient.balance` | `Nedovoljan balans (%s)` |
| `project_ended_template`, `values-az` | `project.ended.template` | `Tamamlandı %s` |
| `project_ended_template`, `values-fa` | `project.ended.template` | `پایان یافت %s` |
| `remove_pool_confirmation_description`, `values-nl` | `remove.pool.confirmation.description` | `De output wordt geschat. Als de prijs meer dan %s%% verandert, wordt uw transactie teniet gedaan.` |

The first four values had omitted the argument used by their Android callsites.
The Dutch value had a literal `{slippageTolerance}` token, so the formatted
slippage value could not appear. No new translation wording was invented.

The three referral text resources contain a literal `10%` and are displayed
without format arguments. Their default and all 23 localized entries now declare
`formatted="false"`, removing 37 false `StringFormatCount` lint warnings. Seven
German, Finnish, and French entries previously used `10 %%`; the resource table
confirmed that this displayed two percent signs. They now use `10 %` in source
and in the processed Release resource table. The other referral wording remains
unchanged.

The Middle Egyptian verifier pins the bundled Noto Sans Egyptian Hieroglyphs
license by SHA-256. The tracked text had lost one official trailing space; it
now matches the [upstream Noto OFL](https://github.com/notofonts/egyptian-hieroglyphs/blob/main/OFL.txt)
at `dc8114a49f5bb53bad3d99ee52cbb245e98076438d59428a258720258494de68`.
The path-specific `.gitattributes` setting allows that exact upstream byte while
keeping `git diff --check` clean.

Verification: all 24 affected XML catalogs parse;
`node scripts/verify-middle-egyptian-localization.mjs` passes its 747-message
and font/license checks; `node scripts/verify-production-modernization.mjs`
reports zero structural failures; and strict
`:app:processProductionReleaseResources` passes. Lint retains the separate
`MissingTranslation` release hold; no missing-translation suppression was added.
