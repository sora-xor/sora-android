# Android 16 KB app-flow smoke — 2026-09-25

This is bounded runtime evidence for the production-flavor Debug APK built from
`2aa1a378360395b5b534f40ed7dcd5026087255f`. APK SHA-256:
`feabcb62e1d884d49362716faff9a3b63da0e084c189258c9c949361ffde5665`.
The later candidate commit `e63663c8` changed only the native audit document;
the tested APK bytes were unchanged.

## Device and isolation

- Dedicated API 36 `arm64-v8a` Android emulator; `adb shell getconf PAGESIZE`
  returned `16384`.
- Wi-Fi and mobile data were disabled before app launch. The dedicated emulator
  was shut down after the smoke; an unrelated pre-existing emulator was left
  untouched.
- No real wallet phrase, live account, transaction, signing, or production
  network mutation was used. A generated test phrase was not retained in the
  evidence artifacts.

## Observed flow

1. `am start -W` cold-launched the app successfully (`Status: ok`, total time
   1561 ms); `libjnidispatch.so` loaded.
2. Welcome → Create account → test-only account name → three recovery-phrase
   safety acknowledgments → generated 24-word display → word confirmation →
   Welcome. The account was not finalized.
3. Import account → source chooser → Passphrase and Raw Seed entry. Invalid
   test input left Continue disabled; no import was performed.
4. App PID `3525` remained unchanged through the navigation. A logcat scan from
   before cold launch found zero Java fatal exceptions, app ANRs, native fatal
   signals, `UnsatisfiedLinkError`, or JNI detected errors. Offline DNS and
   WebSocket retries occurred as expected with networking disabled.

This establishes startup and navigation stability for the tested APK on a
16 KB arm64 emulator. It does not establish complete wallet creation, online
sync, signing, KYC, all native-library paths, a signed/minified Release build,
or retained physical-device migration. The local operator handoff retains the
UI capture and logcat scan without recording the generated phrase.
