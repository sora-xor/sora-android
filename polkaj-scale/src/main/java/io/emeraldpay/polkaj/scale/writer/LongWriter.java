package io.emeraldpay.polkaj.scale.writer;

import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;

import java.io.IOException;

// SORA: added to upstream polkaj 0.2.3 (as in the polkaj-scale 0.2.3 build SORA used).
/** Writes a 64-bit value, little-endian. */
public class LongWriter implements ScaleWriter<Long> {
    @Override
    public void write(ScaleCodecWriter wrt, Long value) throws IOException {
        wrt.directWrite(value.byteValue());
        for (int shift = 8; shift < 64; shift += 8) {
            wrt.directWrite((byte) (int) (value.longValue() >> shift));
        }
    }
}
