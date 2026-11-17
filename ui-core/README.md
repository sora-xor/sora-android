# ui-core

SORA's Compose UI components (`jp.co.soramitsu.ui_core`): the theme, colours and
typography, buttons, text and number inputs, cards, toolbars, menu items and the
asset and token-icon widgets the app screens are built from.
It is built from source here, replacing the `jp.co.soramitsu:ui-core:0.2.39` AAR that
used to be vendored in `vendor/soramitsu-maven`.

- `src/main/java`: the Kotlin sources of that release. They come from
  https://github.com/soramitsu/android-ui-libraries at
  `f2b6f27adef101fa997432e46886dcbf3625ac77`. The release's generated
  `BuildConfig.java` is left out; nothing reads it. SORA added `lit/` (the lit materials)
  and `sacred/` (the launch scene) here, and the buttons, `Card`, `CategoryItem` and
  `ButtonLoader` were changed to draw with them.
- `src/main/res`: the AAR's resources: vector drawables and the Inter and Sora fonts.
  `ic_loading.xml` went with the old button loader.

## Removed sources

These files of the release were deleted because nothing can reach them: no source in this
repository (any module, flavor or test, layouts, ProGuard rules, build scripts) names them,
no class file in the vendored binaries or the locked third-party dependencies refers to the
`jp.co.soramitsu.ui_core` package, and nothing loads them by reflection or by resource name.

- `component/searchview/` (`SearchView`, `SearchViewState`). The toolbar's own search
  stays.
- `component/text/HtmlText.kt`, `component/checkbox/CheckBoxButtonScreen.kt` (the app has
  its own `CheckboxButton` in `:common`) and `modifier/debounce/`.
- In `component/input/number`: `CurrencyGroupingVisualTransformationSuffix`,
  `CurrencyGroupingOffsetMapping` and the `Preview.kt` that was their only user.
  `CurrencyGroupingVisualTransformation` stays.
- The drawables `ic_circle_empty.xml` and `ic_cirlce_selected.xml`, used only by the removed
  checkbox, and `ic_microphone.xml`, which nothing used (its lint exception went with it).

## License

Apache-2.0 (`LICENSE`, from that repository, which is private). The Inter and Sora fonts
are SIL Open Font License 1.1.
