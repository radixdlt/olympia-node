package org.radix.utils;

import java.util.Arrays;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Longs;

import com.google.common.annotations.VisibleForTesting;

/**
 * Fixed size bit array.
 * The intention with this class is to provide a simplified set
 * of accessor methods ({@code get}, {@code set}, {@code clear}),
 * together with a reasonably efficient method for copying bit
 * values between arrays.
 */
public final class BArray {

	private static final int WORD_BITS  = 64; // Needs to be Long.SIZE and power of 2
	private static final int WORD_BYTES = WORD_BITS / Byte.SIZE;
	private static final int BITS_SHIFT = 6;  // Log2 BITS_WORD
	private static final int BITS_MASK  = WORD_BITS - 1;

	@VisibleForTesting
	final long[] bitArray;

	/**
	 * Create an empty bit array with at least the specified number of bits.
	 * Note that {@code numBits} is currently rounded up to the next
	 * multiple of  64.
	 *
	 * @param numBits The number of bits required in the bit array
	 */
	public BArray(int numBits) {
		if (numBits < 0) {
			throw new IllegalArgumentException("numBits bust be >= 0: " + numBits);
		}
		this.bitArray = new long[(numBits + BITS_MASK) >>> BITS_SHIFT];
	}

	private BArray(long[] bitArray) {
		this.bitArray = bitArray;
	}

	/**
	 * Copies specified bit array.
	 *
	 * @param toCopy The bit array to copy
	 */
	public static BArray copyOf(BArray toCopy) {
		return new BArray(toCopy.bitArray.clone());
	}

	/**
	 * Constructs bit array from specified bits.
	 * Note that the actual number of bits in the underlying bit array
	 * will be rounded up to the next multiple of 64.
	 *
	 * @param toCopy The bit array to copy
	 */
	public static BArray valueOf(byte[] bytes) {
		if (bytes == null) {
			throw new NullPointerException("bytes may not be null");
		}
		int count = bytes.length;
		long[] values = new long[(count + WORD_BYTES - 1) / WORD_BYTES];
		int index = 0;
		int offset = 0;
		while (count >= WORD_BYTES) {
			values[index] = Longs.fromByteArray(bytes, offset);
			index += 1;
			offset += WORD_BYTES;
			count -= WORD_BYTES;
		}
		if (count > 0) {
			long value = 0L;
			int extra = WORD_BYTES - count;
			for (int i = 0; i < count; ++i) {
				value <<= Byte.SIZE;
				value |= bytes[offset++] & 0xFF;
			}
			value <<= Byte.SIZE * extra;
			values[index] = value;
		}
		return new BArray(values);
	}

	/**
	 * Returns size of bit array, in bits.
	 * @return size of bit array, in bits
	 */
	public int size() {
		return this.bitArray.length << BITS_SHIFT;
	}

	/**
	 * Converts this bit array into a byte array.
	 * Bits are ordered left to right, so bit 0 will be in the leftmost
	 * bit (ie bit 7) of the first element of the returned array.
	 *
	 * @return this bit array as a byte array
	 */
	public byte[] toByteArray() {
		int bytes = this.bitArray.length * WORD_BYTES;
		byte[] values = new byte[bytes];
		int byteIndex = 0;
		for (int i = 0; i < this.bitArray.length; ++i) {
			Longs.copyTo(this.bitArray[i], values, byteIndex);
			byteIndex += WORD_BYTES;
		}
		return values;
	}

	/**
	 * Sets all bits to zero.
	 */
	public void clear() {
		Arrays.fill(this.bitArray, 0L);
	}

	/**
	 * Returns the value of the specified bit as a boolean.
	 *
	 * @param bit The bit to return
	 * @return The value of the bit
	 */
	public boolean get(int bit) {
		int word = bit >>> BITS_SHIFT;
		int element = BITS_MASK - (bit & BITS_MASK);
		long mask = 1L << element;
		return (this.bitArray[word] & mask) != 0;
	}

	/**
	 * Sets the value of the specified bit to {@code true}.
	 *
	 * @param bit The bit to set
	 */
	public void set(int bit) {
		int word = bit >>> BITS_SHIFT;
		int element = BITS_MASK - (bit & BITS_MASK);
		long mask = 1L << element;
		this.bitArray[word] |= mask;
	}

	/**
	 * Clears the value of the specified bit to {@code false}.
	 *
	 * @param bit The bit to clear
	 */
	public void clear(int bit) {
		int word = bit >>> BITS_SHIFT;
		int element = BITS_MASK - (bit & BITS_MASK);
		long mask = ~(1L << element);
		this.bitArray[word] &= mask;
	}

	/**
	 * Copies bits from the specified bit array to this bit array.
	 * Some attempt is made to do this in a reasonably efficient way.
	 *
	 * @param source The source bits to copy
	 * @param destOffset The offset within this bit array to write copied bits
	 * @param count Number of bits to copy
	 */
	public void copyBits(BArray source, int destOffset, int count) {
		int dst = destOffset;
		int src = 0;
		while (count > 0) {
			int dstWord = dst >>> BITS_SHIFT;
			int dstElement = BITS_MASK - (dst & BITS_MASK);
			int srcWord = src >>> BITS_SHIFT;
			int srcElement = BITS_MASK - (src & BITS_MASK);
			int bits = Math.min(count, Math.min(dstElement + 1, srcElement + 1));
			long data = readBits(source.bitArray, srcWord, srcElement, bits);
			writeBits(this.bitArray, dstWord, dstElement, bits, data);
			dst += bits;
			src += bits;
			count -= bits;
		}
	}

	@VisibleForTesting
	static long readBits(long[] array, int word, int startBit, int count) {
		if (count == WORD_BITS) {
			return array[word];
		} else {
			long mask = (1L << count) - 1L;
			int shift = startBit + 1 - count;
			return (array[word] & (mask << shift)) >>> shift;
		}
	}

	@VisibleForTesting
	static void writeBits(long[] array, int word, int startBit, int count, long data) {
		if (count == WORD_BITS) {
			array[word] = data;
		} else {
			long mask = (1L << count) - 1L;
			int shift = startBit + 1 - count;
			long value = array[word] & ~(mask << shift);
			array[word] = value | (data << shift);
		}
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.bitArray);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof BArray) {
			BArray other = (BArray) obj;
			return Arrays.equals(this.bitArray, other.bitArray);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), Bytes.toHexString(toByteArray()));
	}
}
