/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.io.ExposedByteArrayOutputStream;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.EncodingParams;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

public class GolombLongEncoding implements Encoding<Long> {
    private static final EncodingID ENCODING_ID = EncodingID.GOLOMB;
    private int m;
    private int offset;

    public GolombLongEncoding() {
    }

    @Override
    public EncodingID id() {
        return ENCODING_ID;
    }

    public static EncodingParams toParam(final int offset, final int m) {
        final GolombLongEncoding e = new GolombLongEncoding();
        e.offset = offset;
        e.m = m;
        return new EncodingParams(ENCODING_ID, e.toByteArray());
    }

    @Override
    public byte[] toByteArray() {
        final ByteBuffer buf = ByteBuffer.allocate(10);
        ITF8.writeUnsignedITF8(offset, buf);
        ITF8.writeUnsignedITF8(m, buf);
        buf.flip();
        final byte[] array = new byte[buf.limit()];
        buf.get(array);
        return array;
    }

    @Override
    public void fromByteArray(final byte[] data) {
        final ByteBuffer buf = ByteBuffer.wrap(data);
        offset = ITF8.readUnsignedITF8(buf);
        m = ITF8.readUnsignedITF8(buf);
    }

    @Override
    public BitCodec<Long> buildCodec(final Map<Integer, InputStream> inputMap,
                                     final Map<Integer, ExposedByteArrayOutputStream> outputMap) {
        return new GolombLongCodec(offset, m);
    }

}
