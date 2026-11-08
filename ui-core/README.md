# ui-core

SORA's Compose UI components (`jp.co.soramitsu.ui_core`): the theme, colours and
typography, buttons, text and number inputs, cards, toolbars, search, menu items and the
asset and token-icon widgets the app screens are built from.
It is built from source here, replacing the `jp.co.soramitsu:ui-core:0.2.39` AAR that
used to be vendored in `vendor/soramitsu-maven`.

- `src/main/java`: the Kotlin sources of that release, unchanged. They come from
  https://github.com/soramitsu/android-ui-libraries at
  `f2b6f27adef101fa997432e46886dcbf3625ac77`. The release's generated
  `BuildConfig.java` is left out; nothing reads it.
- `src/main/res`: the AAR's resources, unchanged: vector drawables and the Inter and
  Sora fonts.

## License

Apache-2.0 (`LICENSE`, from that repository, which is private). The Inter and Sora fonts
are SIL Open Font License 1.1.
