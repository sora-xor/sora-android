# Middle Egyptian hieroglyph localization policy

## Locale and writing direction

Android registers this localization as `egy-Egyp` and stores it in
`values-b+egy+Egyp`. `egy` is the ISO language subtag for Ancient Egyptian and
`Egyp` is the ISO 15924 script subtag for Egyptian hieroglyphs. The BCP-47
resource form is necessary because the script is part of the locale identity;
the legacy `values-egy` form would discard it.

Text is normalized as linear, left-to-right Unicode Egyptian Hieroglyphs
(U+13000–U+1342F). It does not use vertical quadrats, mirrored signs, bidi
overrides, or the hieroglyph-format controls beginning at U+13430. Spaces and
modern punctuation are editorial reading aids for a phone UI.

The English selector label is **Middle Egyptian (Hieroglyphs)**. The native
label is `𓌃𓂧𓅱𓀁 𓈖 𓆎𓅓𓏏𓊖` (*mdw n Kmt*, “speech / words of Egypt”). This
label keeps an attested-sign spelling even though the broader catalog uses a
normalized linear spelling.

## Translation register

The translation uses a compact Middle Egyptian UI register:

- verbs in buttons and commands are bare lexical imperatives;
- headings are noun phrases;
- explanatory text uses short nominal or paratactic clauses rather than
  imitating English auxiliaries;
- the spelling is a reproducible linear phonographic normalization built from
  Egyptian uniliteral signs;
- articles that do not contribute meaning are omitted, as Middle Egyptian does
  not mirror the English article system.

The core lexicon follows Raymond O. Faulkner's *A Concise Dictionary of Middle
Egyptian* and is cross-checked against the Thesaurus Linguae Aegyptiae (TLA).
The normalized spelling is not a facsimile of a particular inscription: actual
Middle Egyptian spelling varies by period, medium, and scribe.

## Transparent modern compounds

There are no attested Middle Egyptian words for a mobile wallet, blockchain, QR
code, or decentralized exchange. The localization therefore uses old words in
transparent poetic compounds. These compounds are editorial neologisms and are
**not claimed as attested ancient expressions**.

| Modern concept | Normalized Egyptian | Semantic image |
| --- | --- | --- |
| app | `sš ꜥnḫ` | living / active writing |
| wallet | `pr ḥḏ` | house of silver or money |
| account | `ḥsb` | reckoning or account |
| address | `st n ḥsb` | place of the reckoning |
| network | `jꜣdt nt wꜣwt` | net of roads |
| internet | `jꜣdt n mdw` | net of words |
| node | `sbꜣ n jꜣdt` | gate of the net |
| transaction | `jrt swnt` | deed of exchange |
| market | `st n swnt` | place of exchange |
| asset / token | `jḫt` | property or thing |
| hash | `ḫtm` | seal / identifying impression |
| block | `ḏbt` | brick or ingot |
| block store | `pr n ḏbwt` | house of bricks |
| fee | `bꜣkw` | levy or pay |
| wallet backup | `sš snnw n pr ḥḏ` | second writing of the house of silver |
| password | `mdw sštꜣ` | secret word |
| passphrase / mnemonic | `mdw sḫꜣ` | words of remembrance |
| raw seed | `prt n kꜣt` | unworked seed |
| liquidity | `ḫtw šmw` | goods that move / flow |
| liquidity pool | `š` | lake or pool |
| email | `šꜥt n pt` | letter of the sky |
| clipboard | `šꜥt ḏrt` | tablet of the hand |
| biometric authentication | `ḫꜣj n ẖt; smn rn` | measure of the body; establish the name |
| slippage | `šmt jsw` | movement of price |
| pending | `ḥr wꜣḥ` | waiting / placed in waiting |
| finalized | `tm` | completed |

Modern language and country names in the language selector are phonetic
Egyptian-script adaptations introduced by `rꜣ n` (“speech of”). They identify
modern names; they are not ancient Egyptian ethnonyms.

## Literal preservation and safety

Brands, currency tickers, protocol identifiers, formulas, and technical
abbreviations remain in their canonical Latin form where translating them could
make the product ambiguous or unsafe. Examples include SORA, XOR, Polkaswap,
IrohaConnect, the prediction-market outcome YES, Ethereum, Substrate, JSON, URL,
QR, PIN, TBC, XYK, `v1`, `v2`, and `x*y=k`.

Printf placeholders, `%%` link markers, escaped newlines, percent signs, euro
signs, and every explicit numeric constraint are preserved exactly. In
particular, passphrase lengths (12/24 words), raw-seed length (64 symbols), PIN
lengths (4/6 digits), search bounds (3/64), and referral percentages retain
their source values.

## Generation and validation

The checked-in XML is generated from the English resources and the controlled
lexicon so a new English message cannot silently fall back:

```sh
node scripts/generate-middle-egyptian-localization.mjs
node scripts/verify-middle-egyptian-localization.mjs
```

The verifier requires one Egyptian entry for every translatable common/app
entry, matching plural branches, matching placeholders and explicit numbers,
matching structural markers, preserved identifiers, `formatted="false"`
contracts, hieroglyph-only translated text, linear-LTR content, the language
holder entry, all four Android locale configurations, the pinned font/license
checksums, and every locale-scoped font alias.

## Deterministic Android glyph rendering

The app bundles the unmodified official **Noto Sans Egyptian Hieroglyphs
Regular v2.002** at
`common/src/main/res/font/noto_sans_egyptian_hieroglyphs_regular.ttf` (SHA-256
`38a33a230624671eebedce95bd4237f7b3b2bb1fa25688ff959bed4070a1ea95`). Its
SIL Open Font License 1.1 is shipped inside the app as
`common/src/main/res/raw/noto_sans_egyptian_hieroglyphs_ofl.txt`.

Android and Compose cannot express a Unicode-range cascade inside an app font
family. Replacing the global typeface would also affect every other locale, so
the catalog instead defines locale-qualified resource aliases in
`values-b+egy+Egyp/hieroglyph_font_aliases.xml`. When `egy-Egyp` is active, all
Sora and Inter font resource IDs used by the app's View styles and Compose theme
resolve to the bundled Noto font. Other locales keep their existing font files.
Noto supplies every allowed hieroglyph sign; Android's normal missing-glyph
fallback renders deliberately preserved Latin identifiers, digits, and modern
punctuation.

This mechanism covers the app-owned typography paths. Text rendered by Android
outside the app process (notably the system per-app-language picker), WebView
content, and isolated screens that explicitly request a platform Serif or
Monospace family remain under the operating system's font fallback and cannot
be safely redirected with locale resources. The localized wallet screens do not
use those isolated platform-family overrides.

## References and review boundary

- [Thesaurus Linguae Aegyptiae](https://thesaurus-linguae-aegyptiae.de/) —
  academy-maintained Egyptian lexicon and corpus.
- TLA core glossary entries: [`jḫt`](https://thesaurus-linguae-aegyptiae.de/lemma/30750)
  “thing/property”, [`bꜣkw`](https://thesaurus-linguae-aegyptiae.de/lemma/53890)
  “taxes/pay”, [`swnt`](https://thesaurus-linguae-aegyptiae.de/lemma/130160)
  “trade/price”, and [`ḏbt`](https://thesaurus-linguae-aegyptiae.de/lemma/183120)
  “brick/ingot”.
- [Faulkner dictionary references and searchable entries](https://middleegyptian.azurewebsites.net/Search/References)
  — the controlled vocabulary's principal English–Middle Egyptian reference.
- [Unicode Egyptian Hieroglyphs chart](https://www.unicode.org/charts/PDF/U13000.pdf)
  — sign encoding used by the resource files.
- [Noto Egyptian Hieroglyphs](https://github.com/notofonts/egyptian-hieroglyphs)
  — upstream for the bundled v2.002 font and OFL.
- [IANA Language Subtag Registry](https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry)
  — definitions of `egy` and `Egyp`.

This is a documented editorial reconstruction for modern software, not a claim
that the complete UI sentences or compounds occur in an ancient source.
Production publication should still receive an Egyptologist's copy review,
especially if a future change adds legal, financial, or recovery language.
