package io.emeraldpay.polkaj.scale.writer;

import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;

import java.io.IOException;

// SORA: added to upstream polkaj 0.2.3 (as in the polkaj-scale 0.2.3 build SORA used).
/** Writes a string as its UTF-8 bytes (see ScaleCodecWriter.writeString). */
public class StringWriter implements ScaleWriter<String> {
    @Override
    public void write(ScaleCodecWriter wrt, String value) throws IOException {
        wrt.writeString(value);
    }
}
