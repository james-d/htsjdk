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

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

import java.io.IOException;

class GammaIntegerCodec extends AbstractBitCodec<Integer> {
    private int offset = 0;

    public GammaIntegerCodec(final int offset) {
        this.offset = offset;
    }

    @Override
    public final Integer read(final BitInputStream bis) throws IOException {
        int len = 1;
        final boolean lenCodingBit = false;
        //noinspection ConstantConditions,PointlessBooleanExpression
        while (bis.readBit() == lenCodingBit)
            len++;
        final int readBits = bis.readBits(len - 1);
        final int value = readBits | 1 << (len - 1);
        return value - offset;
    }

    @Override
    public final long write(final BitOutputStream bos, final Integer value) throws IOException {
        if (value + offset < 1)
            throw new IllegalArgumentException("Gamma codec handles only positive values: " + value);

        final long newValue = value + offset;
        final int betaCodeLength = 1 + (int) (Math.log(newValue) / Math.log(2));
        if (betaCodeLength > 1)
            bos.write(0L, betaCodeLength - 1);

        bos.write(newValue, betaCodeLength);
        return betaCodeLength * 2 - 1;
    }

    @Override
    public final long numberOfBits(final Integer value) {
        final long newValue = value + offset;
        if (newValue < 1)
            throw new RuntimeException("Invalid valid: " + newValue);
        final int betaCodeLength = 1 + (int) (Math.log(newValue) / Math.log(2));
        return betaCodeLength * 2 - 1;
    }

    @Override
    public Integer read(final BitInputStream bis, final int len) throws IOException {
        throw new RuntimeException("Not implemented.");
    }

}
