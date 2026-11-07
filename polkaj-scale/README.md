# polkaj-scale

The SCALE codec (`io.emeraldpay.polkaj.scale`) that xsubstrate and `:sorasubstrate` use, built from
source. It replaces `io.emeraldpay.polkaj:polkaj-scale:0.2.3`, which was vendored as a jar that came
from a Soramitsu fork whose source was never published (soramitsu/sora2-load-library-java kept
only the binary).

- Upstream: https://github.com/emeraldpay/polkaj tag `v0.2.3` (`3227a8ef605cd65b29b1cb2648a02e3a2a9c2419`),
  `polkaj-scale/src/main/java`. License: Apache-2.0 (`LICENSE`).
- Added, as the fork had them (marked "SORA" in the source):
  - `ShortReader` and `LongReader`: 16- and 64-bit little-endian signed reads;
  - `ShortWriter`, `LongWriter` and `StringWriter`;
  - `ScaleCodecReader.readShort/readLong` and the `SHORT_READER`/`LONG_READER` fields;
  - `ScaleCodecWriter.writeString` (UTF-8 bytes via `writeByteArray`), `writeShort`, `writeLong`,
    and the `SHORT_WRITER`/`LONG_WRITER` fields.
- Changed for Android API 26, as the fork did. These do not change behaviour:
  - `Optional.isPresent()` instead of `isEmpty()`;
  - `(ByteBuffer) buf.flip()` casts;
  - `BigInteger.valueOf(2)` instead of `BigInteger.TWO`.

The fork's behaviour was recovered from its bytecode. All other classes match upstream v0.2.3
apart from Java 8 code generation. One harness was compiled against the old jar and against this
source and fed 3,000 seeded cases through every reader and writer. The outputs were identical,
exceptions included.

To rerun the comparison, take the old jar from git history
(`vendor/soramitsu-maven/io/emeraldpay/polkaj/polkaj-scale/0.2.3/polkaj-scale-0.2.3.jar`). Compile
`src/test/compat/PolkajHarness.java` against each one and run `PolkajHarness 3000`. Both must print
the same 117,066 lines, SHA-256 `768e30296071662a2dbd89771bd2135f6ecb37e98f051ad78b1bd8cf266216ab`,
including 25,193 identical exception results.
