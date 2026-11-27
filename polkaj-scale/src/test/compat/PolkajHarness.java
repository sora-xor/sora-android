import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.reader.*;
import io.emeraldpay.polkaj.scale.writer.*;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

/** Same source compiled against the old polkaj-scale jar and the in-repo one; outputs must match. */
public class PolkajHarness {
    interface Body { void run(ScaleCodecWriter w) throws Exception; }
    interface Read { Object run(ScaleCodecReader r) throws Exception; }

    static final StringBuilder out = new StringBuilder();

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }

    static void write(String tag, Body body) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ScaleCodecWriter w = new ScaleCodecWriter(buf)) {
            body.run(w);
            w.flush();
            out.append(tag).append(' ').append(hex(buf.toByteArray())).append('\n');
        } catch (Throwable e) {
            out.append(tag).append(" ! ").append(e.getClass().getName()).append(": ").append(e.getMessage()).append('\n');
        }
    }

    static void read(String tag, byte[] data, Read body) {
        try {
            ScaleCodecReader r = new ScaleCodecReader(data);
            Object v = body.run(r);
            String shown = v instanceof byte[] ? hex((byte[]) v) : String.valueOf(v);
            out.append(tag).append(' ').append(shown).append('\n');
        } catch (Throwable e) {
            out.append(tag).append(" ! ").append(e.getClass().getName()).append(": ").append(e.getMessage()).append('\n');
        }
    }

    public static void main(String[] args) {
        Random rnd = new Random(20261107L);
        int n = Integer.parseInt(args[0]);
        for (int loop = 0; loop < n; loop++) {
            final int i = loop;
            final int iv = rnd.nextInt();
            final long lv = rnd.nextLong();
            final short sv = (short) rnd.nextInt();
            final int small = rnd.nextInt(1 << 30);
            final int ub = rnd.nextInt(512) - 128;
            final byte[] bytes = new byte[rnd.nextInt(80)];
            rnd.nextBytes(bytes);
            final byte[] b32 = new byte[rnd.nextBoolean() ? 32 : rnd.nextInt(40)];
            rnd.nextBytes(b32);
            final String str = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            final BigInteger big = new BigInteger(rnd.nextInt(130), rnd);
            final long u32 = rnd.nextLong() >>> rnd.nextInt(64);
            write("W-short " + i, w -> w.writeShort(sv));
            write("W-long " + i, w -> w.writeLong(lv));
            write("W-string " + i, w -> w.writeString(str));
            write("W-stringwriter " + i, w -> w.write(new StringWriter(), str));
            write("W-shortwriter " + i, w -> w.write(new ShortWriter(), sv));
            write("W-longwriter " + i, w -> w.write(new LongWriter(), lv));
            write("W-byte " + i, w -> w.writeByte(ub));
            write("W-u16 " + i, w -> w.writeUint16(iv & 0xffff));
            write("W-u32i " + i, w -> w.writeUint32(iv));
            write("W-u32l " + i, w -> w.writeUint32(u32));
            write("W-compact " + i, w -> w.writeCompact(small >>> (i % 31)));
            write("W-compactbig " + i, w -> w.write(new CompactBigIntWriter(), big));
            write("W-compactulong " + i, w -> w.write(new CompactULongWriter(), u32));
            write("W-bytes " + i, w -> w.writeByteArray(bytes));
            write("W-u256 " + i, w -> w.writeUint256(b32));
            write("W-opt " + i, w -> w.writeOptional(new UInt16Writer(), (i % 3 == 0) ? Optional.empty() : Optional.of(iv & 0xffff)));
            write("W-optbool " + i, w -> w.writeOptional(new BoolWriter(), (i % 3 == 0) ? Optional.empty() : Optional.of(i % 2 == 0)));
            write("W-boolopt " + i, w -> w.write(new BoolOptionalWriter(), (i % 3 == 0) ? Optional.<Boolean>empty() : Optional.of(i % 2 == 0)));
            write("W-ulong32 " + i, w -> w.write(new ULong32Writer(), u32));
            write("W-ubyte " + i, w -> w.write(new UByteWriter(), ub));

            final byte[] data = new byte[rnd.nextInt(48)];
            rnd.nextBytes(data);
            if (i % 5 == 0 && data.length > 0) data[0] = (byte) (data[0] & 0x03);
            read("R-short " + i, data, ScaleCodecReader::readShort);
            read("R-long " + i, data, ScaleCodecReader::readLong);
            read("R-shortreader " + i, data, r -> r.read(new ShortReader()));
            read("R-longreader " + i, data, r -> r.read(new LongReader()));
            read("R-int32 " + i, data, r -> r.read(new Int32Reader()));
            read("R-byte " + i, data, ScaleCodecReader::readByte);
            read("R-ubyte " + i, data, ScaleCodecReader::readUByte);
            read("R-u16 " + i, data, ScaleCodecReader::readUint16);
            read("R-u32 " + i, data, ScaleCodecReader::readUint32);
            read("R-compact " + i, data, ScaleCodecReader::readCompactInt);
            read("R-compactbig " + i, data, r -> r.read(new CompactBigIntReader()));
            read("R-bool " + i, data, ScaleCodecReader::readBoolean);
            read("R-boolopt " + i, data, r -> r.read(new BoolOptionalReader()));
            read("R-opt " + i, data, r -> r.readOptional(new UInt16Reader()));
            read("R-u256 " + i, data, ScaleCodecReader::readUint256);
            // Length-prefixed reads: keep the prefix a single-byte compact so a random
            // prefix cannot ask for gigabytes; it still over-runs the data at times.
            final byte[] prefixed = data.clone();
            if (prefixed.length > 0) prefixed[0] = (byte) (prefixed[0] & 0xfc);
            read("R-bytes " + i, prefixed, ScaleCodecReader::readByteArray);
            read("R-bytesn " + i, data, r -> r.readByteArray(Math.min(5, data.length)));
            read("R-string " + i, prefixed, ScaleCodecReader::readString);
            read("R-list " + i, prefixed, r -> r.read(new ListReader<>(new UByteReader())));
        }
        System.out.print(out);
    }
}
