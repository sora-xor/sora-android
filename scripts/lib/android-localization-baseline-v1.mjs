export function verifyAndroidLocalizationBaselineV1(baseline, handoff, gradle) {
  const failures = [];
  const fail = (code) => failures.push(code);
  if (!/abortOnError\s*=\s*true/.test(gradle) ||
      !/checkDependencies\s*=\s*true/.test(gradle) ||
      !/baseline\s*=\s*file\("lint-baseline\.xml"\)/.test(gradle)) {
    fail("ANDROID_LOCALIZATION_LINT_GATE_NOT_ENABLED");
  }
  if (!/^<\?xml version="1\.0" encoding="UTF-8"\?>\s*<issues\b/.test(baseline) ||
      !/<\/issues>\s*$/.test(baseline)) {
    fail("ANDROID_LOCALIZATION_BASELINE_MALFORMED");
  }

  const issues = [...baseline.matchAll(/<issue\b([^>]*)>([\s\S]*?)<\/issue>/g)];
  const issueStarts = [...baseline.matchAll(/<issue\b/g)].length;
  if (issues.length !== issueStarts || issues.length !== handoff.issueCount) {
    fail("ANDROID_LOCALIZATION_BASELINE_ISSUE_COUNT_MISMATCH");
  }
  if (handoff.schemaVersion !== 2 || handoff.sourceLocale !== "en" ||
      handoff.lintIssueId !== "MissingTranslation" ||
      handoff.sourceResource !== "common/src/main/res/values/strings.xml" ||
      !Array.isArray(handoff.strings)) {
    fail("ANDROID_LOCALIZATION_HANDOFF_INVALID");
  }

  const expected = new Map((handoff.strings ?? []).map((entry) => [entry.key, entry]));
  if (expected.size !== handoff.issueCount) {
    fail("ANDROID_LOCALIZATION_HANDOFF_DUPLICATE_KEY");
  }
  const seen = new Set();
  for (const [, attributes, body] of issues) {
    const id = /\bid="([^"]+)"/.exec(attributes)?.[1];
    if (id !== "MissingTranslation") {
      fail("ANDROID_LOCALIZATION_BASELINE_OTHER_ISSUE");
      continue;
    }
    const message = /\bmessage="([^"]+)"/.exec(attributes)?.[1]
      ?.replaceAll("&quot;", '"').replaceAll("&amp;", "&");
    const key = /^"([^"]+)" is not translated in /.exec(message ?? "")?.[1];
    if (!key || seen.has(key) || !expected.has(key)) {
      fail("ANDROID_LOCALIZATION_BASELINE_KEY_MISMATCH");
      continue;
    }
    seen.add(key);
    if (!/<location file="src\/main\/res\/values\/strings\.xml"(?:\s|>)/.test(body)) {
      fail("ANDROID_LOCALIZATION_BASELINE_SOURCE_MISMATCH");
    }
    const locales = [...message.matchAll(/"([a-z]+)" \(([^)]+)\)/g)];
    const entry = expected.get(key);
    if (locales.length !== entry.missingLocales.length ||
        locales.some(([_, locale, name], index) =>
          locale !== entry.missingLocales[index] || name !== handoff.localeNames[locale])) {
      fail("ANDROID_LOCALIZATION_BASELINE_LOCALES_MISMATCH");
    }
  }
  if (seen.size !== expected.size) {
    fail("ANDROID_LOCALIZATION_BASELINE_COVERAGE_MISMATCH");
  }
  return [...new Set(failures)].sort();
}
