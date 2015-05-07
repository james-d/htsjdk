package htsjdk.samtools.cram.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;

/**
 * An output stream that calculates CRC32 checksum of all the bytes written through the stream. The java {@link java.util.zip.CRC32}
 * class is used to internally.
 */
public class CRC32_OutputStream extends FilterOutputStream {

    private final CRC32 crc32 = new CRC32();

    public CRC32_OutputStream(final OutputStream out) {
        super(out);
    }

    @Override
    public void write(@SuppressWarnings("NullableProblems") final byte[] b, final int off, final int len) throws IOException {
        crc32.update(b, off, len);
        out.write(b, off, len);
    }

    @Override
    public void write(final int b) throws IOException {
        crc32.update(b);
        out.write(b);
    }

    @Override
    public void write(@SuppressWarnings("NullableProblems") final byte[] b) throws IOException {
        crc32.update(b);
        out.write(b);
    }

    public long getLongCrc32() {
        return crc32.getValue();
    }

    public byte[] getCrc32_BigEndian() {
        final long value = crc32.getValue();
        return new byte[]{(byte) (0xFF & (value >> 24)),
                (byte) (0xFF & (value >> 16)), (byte) (0xFF & (value >> 8)),
                (byte) (0xFF & value)};
    }

    public byte[] getCrc32_LittleEndian() {
        final long value = crc32.getValue();
        return new byte[]{(byte) (0xFF & (value)),
                (byte) (0xFF & (value >> 8)), (byte) (0xFF & (value >> 16)),
                (byte) (0xFF & (value >> 24))};
    }
}
