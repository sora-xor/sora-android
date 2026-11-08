// SCALE codec (io.emeraldpay.polkaj.scale), built from source in this repository. It replaces the
// io.emeraldpay.polkaj:polkaj-scale:0.2.3 jar that was vendored as a binary from a Soramitsu fork
// whose source was never published. See README.md.
plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile>().configureEach {
    // Android API 26 lacks the Java 9+ library methods upstream used; release 8 catches them.
    options.release.set(8)
}
