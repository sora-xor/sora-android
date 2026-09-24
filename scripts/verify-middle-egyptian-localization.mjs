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
  ["common/src/main/res/values/strings.xml", `common/src/main/res/${localeDirectory}/strings.xml`],
  ["app/src/main/res/values/strings.xml", `app/src/main/res/${localeDirectory}/strings.xml`]
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

function parseResources(relativePath) {
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

  if (resources.size === 0) fail(`${relativePath}: no translatable resources parsed`);
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
    const isNeutral = /[\s\d.,:;!?()\[\]{}%+*/=€·—–•'"\-]/u.test(character);
    if (!isHieroglyph && !isNeutral) {
      fail(`${label}: disallowed non-hieroglyph character ${JSON.stringify(character)} (U+${codePoint.toString(16).toUpperCase()})`);
    }
  }
}

function validatePair(sourcePath, translatedPath) {
  const source = parseResources(sourcePath);
  const translated = parseResources(translatedPath);
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
    validateGlyphText(translatedPath, translatedEntry);
  }

  return source.size;
}

let checked = 0;
for (const [sourcePath, translatedPath] of pairs) checked += validatePair(sourcePath, translatedPath);

for (const flavor of ["production", "soralution", "tsting", "develop"]) {
  const localeConfig = path.join(root, `common/src/${flavor}/res/xml/locale_config.xml`);
  const xml = fs.readFileSync(localeConfig, "utf8");
  if (!xml.includes('<locale android:name="egy-Egyp" />')) {
    fail(`${localeConfig}: egy-Egyp is not registered`);
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

const fontPath = "common/src/main/res/font/noto_sans_egyptian_hieroglyphs_regular.ttf";
const licensePath = "common/src/main/res/raw/noto_sans_egyptian_hieroglyphs_ofl.txt";
if (sha256(fontPath) !== expectedFontSha256) {
  fail(`${fontPath}: expected the unmodified Noto Sans Egyptian Hieroglyphs v2.002 font`);
}
if (sha256(licensePath) !== expectedLicenseSha256) {
  fail(`${licensePath}: expected the matching unmodified SIL OFL 1.1 license`);
}

const fontAliasesPath = `common/src/main/res/${localeDirectory}/hieroglyph_font_aliases.xml`;
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

console.log(`Middle Egyptian localization verified: ${checked} messages, exact placeholders/numbers, glyph-only translated text, locale registrations, and pinned bundled font.`);
