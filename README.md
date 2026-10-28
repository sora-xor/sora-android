# SORA Wallet Android

Build and validate locally with JDK 17 and Android SDK 36:

```sh
./gradlew --dependency-verification=strict :app:assembleProductionDebug
./gradlew --dependency-verification=strict testProductionDebugUnitTest :app:lintProductionDebug
```

See [local validation helpers](scripts/README.md) and the
[release checklist](docs/release-3.9.0/RELEASE-CHECKLIST.md) for emulator tests, signing
inputs, dependency checks and release artifact validation.

## License

First-party SORA Wallet code is licensed under [Apache-2.0](LICENSE).
Third-party components retain their own licenses. See [NOTICE](NOTICE)
for copyright and attribution information.
