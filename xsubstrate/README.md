# xsubstrate

SORA's Substrate client library: runtime metadata and the type registry, SCALE encoding
of storage keys, calls and extrinsics, key derivation from seeds and junctions, JSON
keystores, SS58 addresses and the websocket JSON-RPC client. It is built from source here, replacing the `jp.co.soramitsu:xsubstrate:1.2.7`
AAR that used to be vendored in `vendor/soramitsu-maven`.

- `src/main/java`: the Kotlin sources of that release. They come from
  https://github.com/soramitsu/x-substrate at `f020817a10590f94d6e7c75d040557e1b9afad56`
  (the vendored build's only patch changed dependency coordinates, not sources). The
  generated `BuildConfig.java` of the release is left out; nothing reads it.
- Two calls that Kotlin 2 rejects are replaced, and the code listed under "Removed sources"
  is deleted; nothing else differs from the release.
  In `ExtrinsicExt.kt`, `toLowerCase()` of a signature's encryption-type name (`Sr25519`,
  `Ed25519`, `Ecdsa`) became `lowercase()`; those names have no I, so the result is the
  same in every locale. In `RpcSocket.kt`, `toUpperCase()` of a log tag became `uppercase()`,
  so a device set to Turkish now logs `CONNECTING` instead of `CONNECTİNG`.
- It depends on the in-repo `:polkaj-scale` and `:xcrypto`, as the AAR depended on their
  binaries.

## Removed sources

These parts of the release were deleted because nothing can reach them: no source in this
repository (any module, flavor or test, ProGuard rules, build scripts) names them, no class
file in the vendored binaries or the locked third-party dependencies refers to the
`jp.co.soramitsu.xsubstrate` package, and nothing loads them by reflection. The app compiles
without them, and the key-derivation, signing and JSON-keystore tests pass unchanged.
`app/proguard-rules.pro` keeps all of `jp.co.soramitsu.xsubstrate.**`, so until now this code
also shipped in the release build.

- `icon/` (`IconGenerator`, `Circle`, `Scheme`, `Square`): account icons. The app draws
  avatars with its own `AccountAvatarGenerator`.
- `encrypt/keypair/ethereum/` (`EthereumKeypairFactory`, `Bip32KeypairFactory`,
  `Bip32ExtendedKeyPair`): BIP32 key derivation for Ethereum accounts, which the wallet never
  creates. ECDSA and Ethereum JSON keystores are still read and written by `encrypt/json/`
  (`EcdsaJsonSecretCoder`, `EthereumJsonSecretCoder`), which never used these factories.
- `encrypt/junction/SubstrateJunctionDecoder.kt` and `exceptions/JunctionTypeException.kt`.
  The app derives keys without a derivation path; `BIP32JunctionDecoder` stays.
- `encrypt/qr/QrSharing.kt`: an address-sharing QR format the app does not use.
- `runtime/definitions/types/instances/AddressInstanceConstructor.kt`.
- `wsrpc/SocketServiceProvider.kt` (the app builds its own `SocketService`) and four RPC
  requests nothing sends: `AccountInfoRequest`, `PendingExtrinsicsRequest`,
  `SubscribeRuntimeVersionRequest`, `NodeNetworkTypeRequest`.
- `LinearReconnectStrategy` in `wsrpc/recovery/ReconnectStrategy.kt`.

Kept on purpose, although a source-level scan reported them as unused: `scale/Dsl.kt` and
`scale/Delegate.kt` (their extension functions, such as `uint32()` and `vector()`, declare the
runtime-metadata schemas), `RuntimeMetadataTopReader.magicNumber` (a schema field; removing
it would change how metadata bytes are read), `TypeDefinitionsTree.OverriddenItem` and
`OverriddenConstant` (the Gson model of the type-definitions JSON), `RuntimeSnapshot.overrides`
and all of `encrypt/json/`, including `JsonSeedDecoder.extractImportMetaData`.

`build.gradle.kts` still declares `androidsvg`, which only `IconGenerator` used in this module;
`:common` uses it as well, so the library stays in the app either way.

## License

Soramitsu's x-substrate repository is private and has no license file. The release's
sources jar was already published in `vendor/soramitsu-maven` before this module replaced
it. The app itself is Apache-2.0.
