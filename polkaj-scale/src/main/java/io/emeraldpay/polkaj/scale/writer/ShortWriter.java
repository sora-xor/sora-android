package io.emeraldpay.polkaj.scale.writer;

import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;

import java.io.IOException;

// SORA: added to upstream polkaj 0.2.3 (as in the polkaj-scale 0.2.3 build SORA used).
/** Writes a 16-bit value, little-endian. */
public class ShortWriter implements ScaleWriter<Short> {
    @Override
    public void write(ScaleCodecWriter wrt, Short value) throws IOException {
        wrt.directWrite(value.shortValue());
        wrt.directWrite(value.shortValue() >> 8);
    }
}
