package io.emeraldpay.polkaj.scale.reader;

import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// SORA: added to upstream polkaj 0.2.3 (as in the polkaj-scale 0.2.3 build SORA used).
/** Reads a signed 16-bit little-endian value. */
public class ShortReader implements ScaleReader<Short> {
    @Override
    public Short read(ScaleCodecReader rdr) {
        ByteBuffer buf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(rdr.readByte());
        buf.put(rdr.readByte());
        return ((ByteBuffer) buf.flip()).getShort();
    }
}
