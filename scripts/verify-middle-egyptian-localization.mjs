#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const localeDirectory = "values-b+egy+Egyp";
const expectedFontSha256 = "38a33a230624671eebedce95bd4237f7b3b2bb1fa25688ff959bed4070a1ea95";
const expectedLicenseSha256 = "dc8114a49f5bb53bad3d99ee52cbb245e98076438d59428a258720258494de68";

const pairs = [
  ["common/src/main/res/values", `common/src/nonProduction/res/${localeDirectory}`],
  ["app/src/main/res/values", `app/src/nonProduction/res/${localeDirectory}`]
];

const protectedLiterals = [
  "@sora_happy", "x*y=k", "Polkaswap", "Polkamarkt", "Substrate", "Ethereum", "Instagram",
  "Telegram", "Twitter", "YouTube", "GitHub", "Google", "Medium", "Nexus", "Polkadot",
  "Kusama", "Demeter", "Taira", "PSWAP", "SMART", "KUSD", "JSON",
  "DeFi", "Drive", "ADAR", "APR", "EUR", "ETH", "IBAN", "PIN", "QR", "URL", "TBC",
  "XST", "XYK", "XOR", "SORA", "Wiki", "LP", "IrohaConnect", "YES", "ms", "v1", "v2"
].sort((left, right) => right.length - left.length);

function fail(message) {
  throw new Error(message);
}

function count(text, needle) {
  return text.split(needle).length - 1;
}

function countProtectedLiteral(text, literal) {
  if (!/^[A-Za-z0-9_]+$/.test(literal)) return count(text, literal);
  const escaped = literal.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return [...text.matchAll(new RegExp(`(?<![A-Za-z0-9_])${escaped}(?![A-Za-z0-9_])`, "g"))].length;
}

function sha256(relativePath) {
  return crypto
    .createHash("sha256")
    .update(fs.readFileSync(path.join(root, relativePath)))
    .digest("hex");
}

function parseResources(relativePath, allowEmpty = false) {
  const absolutePath = path.join(root, relativePath);
  const xml = fs.readFileSync(absolutePath, "utf8");
  const resources = new Map();

  for (const match of xml.matchAll(/<string\s+name="([^"]+)"([^>]*)>([\s\S]*?)<\/string>/g)) {
    const [, name, attributes, value] = match;
    if (/\btranslatable="false"/.test(attributes)) continue;
    resources.set(`string:${name}`, {
      attributes,
      name,
      type: "string",
      value
    });
  }

  for (const plural of xml.matchAll(/<plurals\s+name="([^"]+)"([^>]*)>([\s\S]*?)<\/plurals>/g)) {
    const [, name, pluralAttributes, body] = plural;
    if (/\btranslatable="false"/.test(pluralAttributes)) continue;
    for (const item of body.matchAll(/<item\s+quantity="([^"]+)"([^>]*)>([\s\S]*?)<\/item>/g)) {
      const [, quantity, attributes, value] = item;
      resources.set(`plurals:${name}:${quantity}`, {
        attributes: `${pluralAttributes} ${attributes}`,
        name: `${name}[${quantity}]`,
        type: "plurals",
        value
      });
    }
  }

  if (resources.size === 0 && !allowEmpty) fail(`${relativePath}: no translatable resources parsed`);
  return resources;
}

function parseResourceDirectory(relativeDirectory) {
  const resources = new Map();
  for (const name of fs.readdirSync(path.join(root, relativeDirectory)).filter((name) => name.endsWith(".xml")).sort()) {
    for (const [key, entry] of parseResources(`${relativeDirectory}/${name}`, true)) {
      if (resources.has(key)) fail(`${relativeDirectory}: duplicate ${key}`);
      resources.set(key, entry);
    }
  }
  if (resources.size === 0) fail(`${relativeDirectory}: no translatable resources parsed`);
  return resources;
}

function sortedMatches(value, pattern) {
  return [...value.matchAll(pattern)].map((match) => match[0]).sort();
}

function validateGlyphText(relativePath, entry) {
  const label = `${relativePath}:${entry.name}`;
  let remaining = entry.value
    .replace(/%(?:\d+\$)?[sd]/g, "")
    .replace(/%%/g, "")
    .replace(/\\n/g, "");

  for (const literal of protectedLiterals) remaining = remaining.split(literal).join("");

  if (/[A-Za-z]/.test(remaining)) {
    fail(`${label}: untranslated Latin text remains in ${JSON.stringify(entry.value)}`);
  }
  if (/[‪-‮⁦-⁩]/u.test(remaining)) {
    fail(`${label}: bidi control found; the locale must remain linear LTR`);
  }

  for (const character of remaining) {
    const codePoint = character.codePointAt(0);
    // Noto Sans Egyptian Hieroglyphs v2.002 contains the complete encoded sign
    // range. U+13430 and later are format controls, which this linear catalog
    // intentionally excludes.
    const isHieroglyph = codePoint >= 0x13000 && codePoint <= 0x1342f;
    const isNeutral = /[\s\d.,:;!?()\[\]{}%+*/=€·—–…•'"\-]/u.test(character);
    if (!isHieroglyph && !isNeutral) {
      fail(`${label}: disallowed non-hieroglyph character ${JSON.stringify(character)} (U+${codePoint.toString(16).toUpperCase()})`);
    }
  }
}

function validatePair(sourcePath, translatedPath) {
  if (!translatedPath.includes("/src/nonProduction/res/")) fail("Ancient fixture fallback must never ship in production");
  const source = parseResourceDirectory(sourcePath);
  const translated = parseResourceDirectory(translatedPath);
  const fallbackPath = `${translatedPath}/english_fallback.xml`;
  const fallback = parseResources(fallbackPath);
  const fallbackText = fs.readFileSync(path.join(root, fallbackPath), "utf8");
  if (!fallbackText.includes("They are not Akkadian/Egyptian translations and need native review.")) {
    fail(`${fallbackPath}: explicit test-only English fallback notice missing`);
  }
  const missing = [...source.keys()].filter((key) => !translated.has(key));
  const extra = [...translated.keys()].filter((key) => !source.has(key));
  if (missing.length || extra.length) {
    fail(`${translatedPath}: resource mismatch; missing=${missing.join(",") || "none"}; extra=${extra.join(",") || "none"}`);
  }

  for (const [key, sourceEntry] of source) {
    const translatedEntry = translated.get(key);
    const label = `${translatedPath}:${sourceEntry.name}`;

    const sourcePlaceholders = sortedMatches(sourceEntry.value, /%(?:\d+\$)?[sd]|%%/g);
    const translatedPlaceholders = sortedMatches(translatedEntry.value, /%(?:\d+\$)?[sd]|%%/g);
    if (JSON.stringify(sourcePlaceholders) !== JSON.stringify(translatedPlaceholders)) {
      fail(`${label}: placeholder mismatch ${JSON.stringify(sourcePlaceholders)} != ${JSON.stringify(translatedPlaceholders)}`);
    }

    const sourceNumbers = sortedMatches(sourceEntry.value, /\d+/g);
    const translatedNumbers = sortedMatches(translatedEntry.value, /\d+/g);
    if (JSON.stringify(sourceNumbers) !== JSON.stringify(translatedNumbers)) {
      fail(`${label}: numeric constraint mismatch ${JSON.stringify(sourceNumbers)} != ${JSON.stringify(translatedNumbers)}`);
    }

    for (const marker of ["%", "\\n", "•", "€"]) {
      if (count(sourceEntry.value, marker) !== count(translatedEntry.value, marker)) {
        fail(`${label}: structural marker ${JSON.stringify(marker)} was not preserved`);
      }
    }

    for (const literal of protectedLiterals) {
      const sourceCount = countProtectedLiteral(sourceEntry.value, literal);
      if (sourceCount && sourceCount !== countProtectedLiteral(translatedEntry.value, literal)) {
        fail(`${label}: protected literal ${literal} was not preserved exactly`);
      }
    }

    const sourceFormattedFalse = /\bformatted="false"/.test(sourceEntry.attributes);
    const translatedFormattedFalse = /\bformatted="false"/.test(translatedEntry.attributes);
    if (sourceFormattedFalse !== translatedFormattedFalse) {
      fail(`${label}: formatted=false contract changed`);
    }

    if (translatedEntry.value.trim() === "") fail(`${label}: empty translation`);
    if (fallback.has(key)) {
      // A fallback is not a translation: it must exactly match current English,
      // including safety wording. Native entries still undergo every glyph check.
      if (translatedEntry.value !== sourceEntry.value) fail(`${label}: declared English fallback is stale or altered`);
      fallbackCount += 1;
    } else {
      validateGlyphText(translatedPath, translatedEntry);
      nativeCount += 1;
    }
  }

  return source.size;
}

let checked = 0;
let nativeCount = 0;
let fallbackCount = 0;
for (const [sourcePath, translatedPath] of pairs) checked += validatePair(sourcePath, translatedPath);

// Middle Egyptian is a test-only locale: registered for develop and soralution builds, and
// deliberately absent from the production app's system language list.
for (const flavor of ["soralution", "develop"]) {
  const localeConfig = path.join(root, `common/src/${flavor}/res/xml/locale_config.xml`);
  const xml = fs.readFileSync(localeConfig, "utf8");
  if (!xml.includes('<locale android:name="egy-Egyp" />')) {
    fail(`${localeConfig}: egy-Egyp is not registered`);
  }
}
{
  const localeConfig = path.join(root, "common/src/production/res/xml/locale_config.xml");
  const xml = fs.readFileSync(localeConfig, "utf8");
  if (xml.includes('android:name="egy-Egyp"') || xml.includes('android:name="akk"')) {
    fail(`${localeConfig}: test-only locales must not be offered in production`);
  }
}

const holder = fs.readFileSync(
  path.join(root, "common/src/main/java/jp/co/soramitsu/common/resourses/LanguagesHolder.kt"),
  "utf8"
);
if (!holder.includes('Language("egy-Egyp", R.string.common_egyptian, R.string.common_egyptian_native)')) {
  fail("LanguagesHolder.kt: egy-Egyp language registration is missing");
}

const nativeLabels = fs.readFileSync(
  path.join(root, "common/src/main/res/values/stringsnottranslate.xml"),
  "utf8"
);
const defaultLabels = fs.readFileSync(
  path.join(root, "common/src/main/res/values/strings.xml"),
  "utf8"
);
if (!defaultLabels.includes('<string name="common_egyptian" translatable="false">Middle Egyptian (Hieroglyphs)</string>')) {
  fail("strings.xml: the stable English Middle Egyptian selector label is missing");
}
if (!nativeLabels.includes('<string name="common_egyptian_native" translatable="false">𓌃𓂧𓅱𓀁 𓈖 𓆎𓅓𓏏𓊖</string>')) {
  fail("stringsnottranslate.xml: the attested-sign mdw n Kmt native label is missing");
}

const fontPath = "common/src/nonProduction/res/font/noto_sans_egyptian_hieroglyphs_regular.ttf";
const licensePath = "common/src/nonProduction/assets/licenses/noto_sans_egyptian_hieroglyphs_ofl.txt";
if (sha256(fontPath) !== expectedFontSha256) {
  fail(`${fontPath}: expected the unmodified Noto Sans Egyptian Hieroglyphs v2.002 font`);
}
if (sha256(licensePath) !== expectedLicenseSha256) {
  fail(`${licensePath}: expected the matching unmodified SIL OFL 1.1 license`);
}

const fontAliasesPath = `common/src/nonProduction/res/${localeDirectory}/hieroglyph_font_aliases.xml`;
const fontAliases = fs.readFileSync(path.join(root, fontAliasesPath), "utf8");
const expectedFontAliases = [
  "sora_thin", "sora_extralight", "sora_light", "sora_regular", "sora_semibold", "sora_bold", "sora_extrabold",
  "inter_thin", "inter_extralight", "inter_light", "inter_regular", "inter_semibold", "inter_bold", "inter_extrabold"
];
for (const alias of expectedFontAliases) {
  const declaration = `<item name="${alias}" type="font">@font/noto_sans_egyptian_hieroglyphs_regular</item>`;
  if (!fontAliases.includes(declaration)) {
    fail(`${fontAliasesPath}: locale-scoped alias ${alias} is missing or targets the wrong font`);
  }
}

for (const module of ["common", "app"]) {
  for (const locale of ["values-b+akk", localeDirectory]) {
    if (fs.existsSync(path.join(root, module, "src/main/res", locale))) {
      fail(`${module}: ancient fixture locale still packaged in the main source set`);
    }
  }
}
if (fs.existsSync(path.join(root, "common/src/main/res/font/noto_sans_egyptian_hieroglyphs_regular.ttf"))) {
  fail("Ancient fixture font still packaged in the main source set");
}
const commonBuild = fs.readFileSync(path.join(root, "common/build.gradle.kts"), "utf8");
for (const flavor of ["develop", "soralution"]) {
  if (!commonBuild.includes(`getByName("${flavor}").res.directories.add(file("src/nonProduction/res").absolutePath)`) ||
      !commonBuild.includes(`getByName("${flavor}").assets.directories.add(file("src/nonProduction/assets").absolutePath)`)) {
    fail(`common: ${flavor} must retain ancient fixture resources and font license`);
  }
}
const appBuild = fs.readFileSync(path.join(root, "app/build.gradle.kts"), "utf8");
if (!/if\s*\(name in setOf\("develop", "soralution"\)\)\s*\{\s*res\.directories\.add\(file\("src\/nonProduction\/res"\)\.absolutePath\)/.test(appBuild)) {
  fail("app: develop/soralution must retain ancient fixture resources");
}
console.log(`Middle Egyptian fixtures verified: ${checked} messages (${nativeCount} glyph-only native entries, ${fallbackCount} explicitly declared English fallback entries); exact placeholders/numbers, non-production locale registration, and pinned font/license.`);
