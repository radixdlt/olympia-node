/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */
package com.radixdlt.tree.serialization.rlp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.spongycastle.util.encoders.Hex;

public class ByteUtil {

    private ByteUtil() { }

    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /**
     * Creates a copy of bytes and appends b to the end of it
     */
    public static byte[] appendByte(byte[] bytes, byte b) {
        byte[] result = Arrays.copyOf(bytes, bytes.length + 1);
        result[result.length - 1] = b;
        return result;
    }

    /**
     * The regular {@link java.math.BigInteger#toByteArray()} method isn't quite what we often need:
     * it appends a leading zero to indicate that the number is positive and may need padding.
     *
     * @param b the integer to format into a byte array
     * @param numBytes the desired size of the resulting byte array
     * @return numBytes byte long array.
     */
    public static byte[] bigIntegerToBytes(BigInteger b, int numBytes) {
        if (b == null) {
            return null;
        }
        byte[] bytes = new byte[numBytes];
        byte[] biBytes = b.toByteArray();
        int start = (biBytes.length == numBytes + 1) ? 1 : 0;
        int length = Math.min(biBytes.length, numBytes);
        System.arraycopy(biBytes, start, bytes, numBytes - length, length);
        return bytes;
    }

    /**
     * Omitting sign indication byte.
     * <br><br>
     * Instead of {@link org.spongycastle.util.BigIntegers#asUnsignedByteArray(BigInteger)}
     * <br>we use this custom method to avoid an empty array in case of BigInteger.ZERO
     *
     * @param value - any big integer number. A <code>null</code>-value will return <code>null</code>
     * @return A byte array without a leading zero byte if present in the signed encoding.
     * 		BigInteger.ZERO will return an array with length 1 and byte-value 0.
     */
    public static byte[] bigIntegerToBytes(BigInteger value) {
        if (value == null) {
            return null;
        }

        byte[] data = value.toByteArray();

        if (data.length != 1 && data[0] == 0) {
            byte[] tmp = new byte[data.length - 1];
            System.arraycopy(data, 1, tmp, 0, tmp.length);
            data = tmp;
        }
        return data;
    }

    /**
     * Returns the amount of nibbles that match each other from 0 ...
     * 	amount will never be larger than smallest input
     *
     * @param a - first input
     * @param b - second input
     * @return Number of bytes that match
     */
    public static int matchingNibbleLength(byte[] a, byte[] b) {
        int i = 0;
        int length = a.length < b.length ? a.length : b.length;
        while (i < length) {
            if (a[i] != b[i]) {
                break;
            }
            i++;
        }
        return i;
    }

    /**
     * Converts a long value into a byte array.
     *
     * @param val - long value to convert
     * @return <code>byte[]</code> of length 8, representing the long value
     */
    public static byte[] longToBytes(long val) {
        return ByteBuffer.allocate(8).putLong(val).array();
    }

    /**
     * Convert a byte-array into a hex String.<br>
     * Works similar to {@link Hex#toHexString}
     * but allows for <code>null</code>
     *
     * @param data - byte-array to convert to a hex-string
     * @return hex representation of the data.<br>
     * 		Returns an empty String if the input is <code>null</code>
     *
     * @see Hex#toHexString
     */
    public static String toHexString(byte[] data) {
        return data == null ? "" : Hex.toHexString(data);
    }

    /**
     * Calculate packet length
     * @param msg byte[]
     * @return byte-array with 4 elements
     */
    public static byte[] calcPacketLength(byte[] msg) {
        int msgLen = msg.length;
        byte[] len = {
                (byte) ((msgLen >> 24) & 0xFF),
                (byte) ((msgLen >> 16) & 0xFF),
                (byte) ((msgLen >>  8) & 0xFF),
                (byte) ((msgLen) & 0xFF)};
        return len;
    }

    /**
     * Cast hex encoded value from byte[] to int
     *
     * Limited to Integer.MAX_VALUE: 2^32-1 (4 bytes)
     *
     * @param b array contains the values
     * @return unsigned positive int value.
     */
    public static int byteArrayToInt(byte[] b) {
        if (b == null || b.length == 0) {
            return 0;
        }
        return new BigInteger(1, b).intValue();
    }

    /**
     * Turn nibbles to a pretty looking output string
     *
     * 	Example. [ 1, 2, 3, 4, 5 ] becomes '\x11\x23\x45'
     *
     * @param nibbles - getting byte of data [ 04 ] and turning
     *                  it to a '\x04' representation
     * @return pretty string of nibbles
     */
    public static String nibblesToPrettyString(byte[] nibbles) {
        StringBuffer buffer = new StringBuffer();
        for (byte nibble : nibbles) {
            String nibleString = oneByteToHexString(nibble);
            buffer.append("\\x" + nibleString);
        }
        return buffer.toString();
    }

    public static String oneByteToHexString(byte value) {
        String retVal = Integer.toString(value & 0xFF, 16);
        if (retVal.length() == 1) {
            retVal = "0" + retVal;
        }
        return retVal;
    }

    /**
     * Calculate the number of bytes need
     * to encode the number
     *
     * @param val - number
     * @return number of min bytes used to encode the number
     */
    public static int numBytes(String val) {

        BigInteger bInt = new BigInteger(val);
        int bytes = 0;

        while (!bInt.equals(BigInteger.ZERO)) {
            bInt = bInt.shiftRight(8);
            ++bytes;
        }
        if (bytes == 0) {
            ++bytes;
        }
        return bytes;
    }

    /**
     * @param arg - not more that 32 bits
     * @return - bytes of the value pad with complete to 32 zeroes
     */
    public static byte[] encodeValFor32Bits(Object arg) {

        byte[] data;

        // check if the string is numeric
        if (arg.toString().trim().matches("-?\\d+(\\.\\d+)?")) {
            data = new BigInteger(arg.toString().trim()).toByteArray();
        } else if (arg.toString().trim().matches("0[xX][0-9a-fA-F]+")) {  // check if it's hex number
            data = new BigInteger(arg.toString().trim().substring(2), 16).toByteArray();
        } else {
            data = arg.toString().trim().getBytes();
        }

        if (data.length > 32) {
            throw new RuntimeException("values can't be more than 32 byte");
        }

        byte[] val = new byte[32];

        int j = 0;
        for (int i = data.length; i > 0; --i) {
            val[31 - j] = data[i - 1];
            ++j;
        }
        return val;
    }

    /**
     * encode the values and concatenate together
     * @param args Object
     * @return byte[]
     */
    public static byte[] encodeDataList(Object... args) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (Object arg : args) {
            byte[] val = encodeValFor32Bits(arg);
            try {
                baos.write(val);
            } catch (IOException e) {
                throw new Error("Happen something that should never happen ", e);
            }
        }
        return baos.toByteArray();
    }

    public static byte[] stripLeadingZeroes(byte[] data) {

        if (data == null) {
            return null;
        }

        int firstNonZero = 0;
        int i = 0;
        for (; i < data.length; ++i) {
            if (data[i] != 0) {
                firstNonZero = i;
                break;
            }
        }
        if (i == data.length) {
            return new byte[1];
        }
        if (firstNonZero == 0) {
            return data;
        }

        byte[] result = new byte[data.length - firstNonZero];
        System.arraycopy(data, firstNonZero, result, 0, data.length - firstNonZero);

        return result;
    }

    /**
     * increment byte array as a number until max is reached
     *
     * @param bytes byte[]
     *
     * @return boolean
     */
    public static boolean increment(byte[] bytes) {
        final int startIndex = 0;
        int i;
        for (i = bytes.length - 1; i >= startIndex; i--) {
            bytes[i]++;
            if (bytes[i] != 0) {
                break;
            }
        }
        // we return false when all bytes are 0 again
        return (i >= startIndex || bytes[startIndex] != 0);
    }

    /**
     * Utility function to copy a byte array into a new byte array with given size.
     * If the src length is smaller than the given size, the result will be left-padded
     * with zeros.
     *
     * @param value - a BigInteger with a maximum value of 2^256-1
     * @return Byte array of given size with a copy of the <code>src</code>
     */
    public static byte[] copyToArray(BigInteger value) {
        byte[] src = ByteUtil.bigIntegerToBytes(value);
        byte[] dest = ByteBuffer.allocate(32).array();
        System.arraycopy(src, 0, dest, dest.length - src.length, src.length);
        return dest;
    }
}