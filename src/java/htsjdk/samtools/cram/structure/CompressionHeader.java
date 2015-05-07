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
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.encoding.ExternalCompressor;
import htsjdk.samtools.cram.encoding.NullEncoding;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CompressionHeader {
    private static final String RN_readNamesIncluded = "RN";
    private static final String AP_alignmentPositionIsDelta = "AP";
    private static final String RR_referenceRequired = "RR";
    private static final String TD_tagIdsDictionary = "TD";
    private static final String SM_substitutionMatrix = "SM";

    private static final Log log = Log.getInstance(CompressionHeader.class);

    public boolean readNamesIncluded;
    public boolean AP_seriesDelta = true;
    private boolean referenceRequired = true;

    public Map<EncodingKey, EncodingParams> eMap;
    public Map<Integer, EncodingParams> tMap;
    public final Map<Integer, ExternalCompressor> externalCompressors = new HashMap<Integer, ExternalCompressor>();

    public SubstitutionMatrix substitutionMatrix;

    public List<Integer> externalIds;

    public byte[][][] dictionary;

    public CompressionHeader() {
    }

    private CompressionHeader(final InputStream is) throws IOException {
        read(is);
    }

    private byte[][][] parseDictionary(final byte[] bytes) {
        final List<List<byte[]>> dictionary = new ArrayList<List<byte[]>>();
        {
            int i = 0;
            while (i < bytes.length) {
                final List<byte[]> list = new ArrayList<byte[]>();
                while (bytes[i] != 0) {
                    list.add(Arrays.copyOfRange(bytes, i, i + 3));
                    i += 3;
                }
                i++;
                dictionary.add(list);
            }
        }

        int maxWidth = 0;
        for (final List<byte[]> list : dictionary)
            maxWidth = Math.max(maxWidth, list.size());

        final byte[][][] array = new byte[dictionary.size()][][];
        for (int i = 0; i < dictionary.size(); i++) {
            final List<byte[]> list = dictionary.get(i);
            array[i] = list.toArray(new byte[list.size()][]);
        }

        return array;
    }

    private byte[] dictionaryToByteArray() {
        int size = 0;
        for (final byte[][] aDictionary1 : dictionary) {
            for (final byte[] anADictionary1 : aDictionary1) size += anADictionary1.length;
            size++;
        }

        final byte[] bytes = new byte[size];
        final ByteBuffer buf = ByteBuffer.wrap(bytes);
        for (final byte[][] aDictionary : dictionary) {
            for (final byte[] anADictionary : aDictionary) buf.put(anADictionary);
            buf.put((byte) 0);
        }

        return bytes;
    }

    public byte[][] getTagIds(final int id) {
        return dictionary[id];
    }

    public void read(final byte[] data) {
        try {
            read(new ByteArrayInputStream(data));
        } catch (final IOException e) {
            throw new RuntimeException("This should have never happened.");
        }
    }

    void read(final InputStream is) throws IOException {
        { // preservation map:
            final int byteSize = ITF8.readUnsignedITF8(is);
            final byte[] bytes = new byte[byteSize];
            InputStreamUtils.readFully(is, bytes, 0, bytes.length);
            final ByteBuffer buf = ByteBuffer.wrap(bytes);

            final int mapSize = ITF8.readUnsignedITF8(buf);
            for (int i = 0; i < mapSize; i++) {
                final String key = new String(new byte[]{buf.get(), buf.get()});
                if (RN_readNamesIncluded.equals(key))
                    readNamesIncluded = buf.get() == 1;
                else if (AP_alignmentPositionIsDelta.equals(key))
                    AP_seriesDelta = buf.get() == 1;
                else if (RR_referenceRequired.equals(key))
                    referenceRequired = buf.get() == 1;
                else if (TD_tagIdsDictionary.equals(key)) {
                    final int size = ITF8.readUnsignedITF8(buf);
                    final byte[] dictionaryBytes = new byte[size];
                    buf.get(dictionaryBytes);
                    dictionary = parseDictionary(dictionaryBytes);
                } else if (SM_substitutionMatrix.equals(key)) {
                    // parse subs matrix here:
                    final byte[] matrixBytes = new byte[5];
                    buf.get(matrixBytes);
                    substitutionMatrix = new SubstitutionMatrix(matrixBytes);
                } else
                    throw new RuntimeException("Unknown preservation map key: "
                            + key);
            }
        }

        { // encoding map:
            final int byteSize = ITF8.readUnsignedITF8(is);
            final byte[] bytes = new byte[byteSize];
            InputStreamUtils.readFully(is, bytes, 0, bytes.length);
            final ByteBuffer buf = ByteBuffer.wrap(bytes);

            final int mapSize = ITF8.readUnsignedITF8(buf);
            eMap = new TreeMap<EncodingKey, EncodingParams>();
            for (final EncodingKey key : EncodingKey.values())
                eMap.put(key, NullEncoding.toParam());

            for (int i = 0; i < mapSize; i++) {
                final String key = new String(new byte[]{buf.get(), buf.get()});
                final EncodingKey eKey = EncodingKey.byFirstTwoChars(key);
                if (eKey == null) {
                    log.debug("Unknown encoding key: " + key);
                    continue;
                }

                final EncodingID id = EncodingID.values()[buf.get()];
                final int paramLen = ITF8.readUnsignedITF8(buf);
                final byte[] paramBytes = new byte[paramLen];
                buf.get(paramBytes);

                eMap.put(eKey, new EncodingParams(id, paramBytes));

                log.debug(String.format("FOUND ENCODING: %s, %s, %s.",
                        eKey.name(), id.name(),
                        Arrays.toString(Arrays.copyOf(paramBytes, 20))));
            }
        }

        { // tag encoding map:
            final int byteSize = ITF8.readUnsignedITF8(is);
            final byte[] bytes = new byte[byteSize];
            InputStreamUtils.readFully(is, bytes, 0, bytes.length);
            final ByteBuffer buf = ByteBuffer.wrap(bytes);

            final int mapSize = ITF8.readUnsignedITF8(buf);
            tMap = new TreeMap<Integer, EncodingParams>();
            for (int i = 0; i < mapSize; i++) {
                final int key = ITF8.readUnsignedITF8(buf);

                final EncodingID id = EncodingID.values()[buf.get()];
                final int paramLen = ITF8.readUnsignedITF8(buf);
                final byte[] paramBytes = new byte[paramLen];
                buf.get(paramBytes);

                tMap.put(key, new EncodingParams(id, paramBytes));
            }
        }
    }

    public byte[] toByteArray() throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        write(baos);
        return baos.toByteArray();
    }

    void write(final OutputStream os) throws IOException {

        { // preservation map:
            final ByteBuffer mapBuf = ByteBuffer.allocate(1024 * 100);
            ITF8.writeUnsignedITF8(5, mapBuf);

            mapBuf.put(RN_readNamesIncluded.getBytes());
            mapBuf.put((byte) (readNamesIncluded ? 1 : 0));

            mapBuf.put(AP_alignmentPositionIsDelta.getBytes());
            mapBuf.put((byte) (AP_seriesDelta ? 1 : 0));

            mapBuf.put(RR_referenceRequired.getBytes());
            mapBuf.put((byte) (referenceRequired ? 1 : 0));

            mapBuf.put(SM_substitutionMatrix.getBytes());
            mapBuf.put(substitutionMatrix.getEncodedMatrix());

            mapBuf.put(TD_tagIdsDictionary.getBytes());
            {
                final byte[] dBytes = dictionaryToByteArray();
                ITF8.writeUnsignedITF8(dBytes.length, mapBuf);
                mapBuf.put(dBytes);
            }

            mapBuf.flip();
            final byte[] mapBytes = new byte[mapBuf.limit()];
            mapBuf.get(mapBytes);

            ITF8.writeUnsignedITF8(mapBytes.length, os);
            os.write(mapBytes);
        }

        { // encoding map:
            int size = 0;
            for (final EncodingKey eKey : eMap.keySet()) {
                if (eMap.get(eKey).id != EncodingID.NULL)
                    size++;
            }

            final ByteBuffer mapBuf = ByteBuffer.allocate(1024 * 100);
            ITF8.writeUnsignedITF8(size, mapBuf);
            for (final EncodingKey eKey : eMap.keySet()) {
                if (eMap.get(eKey).id == EncodingID.NULL)
                    continue;

                mapBuf.put((byte) eKey.name().charAt(0));
                mapBuf.put((byte) eKey.name().charAt(1));

                final EncodingParams params = eMap.get(eKey);
                mapBuf.put((byte) (0xFF & params.id.ordinal()));
                ITF8.writeUnsignedITF8(params.params.length, mapBuf);
                mapBuf.put(params.params);
            }
            mapBuf.flip();
            final byte[] mapBytes = new byte[mapBuf.limit()];
            mapBuf.get(mapBytes);

            ITF8.writeUnsignedITF8(mapBytes.length, os);
            os.write(mapBytes);
        }

        { // tag encoding map:
            final ByteBuffer mapBuf = ByteBuffer.allocate(1024 * 100);
            ITF8.writeUnsignedITF8(tMap.size(), mapBuf);
            for (final Integer eKey : tMap.keySet()) {
                ITF8.writeUnsignedITF8(eKey, mapBuf);

                final EncodingParams params = tMap.get(eKey);
                mapBuf.put((byte) (0xFF & params.id.ordinal()));
                ITF8.writeUnsignedITF8(params.params.length, mapBuf);
                mapBuf.put(params.params);
            }
            mapBuf.flip();
            final byte[] mapBytes = new byte[mapBuf.limit()];
            mapBuf.get(mapBytes);

            ITF8.writeUnsignedITF8(mapBytes.length, os);
            os.write(mapBytes);
        }
    }

}
