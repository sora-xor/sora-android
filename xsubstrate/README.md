# xsubstrate

SORA's Substrate client library: runtime metadata and the type registry, SCALE encoding
of storage keys, calls and extrinsics, key derivation from seeds and junctions, JSON
keystores, SS58 addresses, account icons and the websocket JSON-RPC client. It is built from source here, replacing the `jp.co.soramitsu:xsubstrate:1.2.7`
AAR that used to be vendored in `vendor/soramitsu-maven`.

- `src/main/java`: the Kotlin sources of that release. They come from
  https://github.com/soramitsu/x-substrate at `f020817a10590f94d6e7c75d040557e1b9afad56`
  (the vendored build's only patch changed dependency coordinates, not sources). The
  generated `BuildConfig.java` of the release is left out; nothing reads it.
- Two calls that Kotlin 2 rejects are replaced; nothing else differs from the release.
  In `ExtrinsicExt.kt`, `toLowerCase()` of a signature's encryption-type name (`Sr25519`,
  `Ed25519`, `Ecdsa`) became `lowercase()`; those names have no I, so the result is the
  same in every locale. In `RpcSocket.kt`, `toUpperCase()` of a log tag became `uppercase()`,
  so a device set to Turkish now logs `CONNECTING` instead of `CONNECTİNG`.
- It depends on the in-repo `:polkaj-scale` and `:xcrypto`, as the AAR depended on their
  binaries.

## License

Soramitsu's x-substrate repository is private and has no license file. The release's
sources jar was already published in `vendor/soramitsu-maven` before this module replaced
it. The app itself is Apache-2.0.
