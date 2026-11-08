package io.emeraldpay.polkaj.scale.reader;

import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// SORA: added to upstream polkaj 0.2.3 (as in the polkaj-scale 0.2.3 build SORA used).
/** Reads a signed 64-bit little-endian value. */
public class LongReader implements ScaleReader<Long> {
    @Override
    public Long read(ScaleCodecReader rdr) {
        ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 8; i++) {
            buf.put(rdr.readByte());
        }
        return ((ByteBuffer) buf.flip()).getLong();
    }
}
