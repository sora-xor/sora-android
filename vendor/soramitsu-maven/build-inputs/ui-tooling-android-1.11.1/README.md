# Material2 device preview controls

This debug-only fork of AndroidX Compose UI Tooling 1.11.1 changes the three
Material imports in `PreviewActivity` and the floating button's `content` argument
to Material2's `text` argument. The application uses Material2. The upstream
activity otherwise brings Material3 into every debug screen and renders preview
parameter controls with a different theme.

On-device previews, reflective invocation, parameter selection and cycling remain
available. All other classes, resources, licenses and notices retain their exact
upstream bytes. The POM/module replace the Material3 dependency with Material2;
the source artifact contains the modified source. Upstream sample sources remain
available from Google Maven. Full tooling is restricted to `debugImplementation`;
preview annotations remain available to all builds.

The inputs come from
`https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-tooling-android/1.11.1/`.
`inputs.json` pins upstream and compiler/dependency SHA-256 hashes. The build uses
JDK 17, Kotlin/Compose compiler 2.3.21 and Android SDK 37.0. Dependencies must first
be available in the Gradle module cache at their pinned coordinates. No dependency
is downloaded or accepted without a matching hash by this script.

```sh
python3 vendor/soramitsu-maven/build-inputs/ui-tooling-android-1.11.1/rebuild.py \
  --java "$JAVA_HOME/bin/java" \
  --android-jar "$ANDROID_HOME/platforms/android-37.0/android.jar" \
  --output /tmp/rebuilt-sora-ui-tooling
```

Compare the four output artifacts with the corresponding local Maven directory.
The repository's independent supply-chain review requirements remain in force;
reproducibility does not claim that independent review has occurred.
