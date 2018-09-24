package com.radixdlt.client.core.serialization;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Serialisation encoding / decoding utilities.
 */
public class SerializationUtils {

	private SerializationUtils() {
		throw new IllegalArgumentException("Can't construct");
	}

	/**
	 * The maximum value we can serialize as a length.
	 * Numbers {@code [0..SERIALIZE_MAX_INT]} can be
	 * serialized by {@link #intLength(int)},
	 * {@link #encodeInt(int, ByteBuffer)} and
	 * {@link #decodeInt(ByteBuffer)}.
	 * <p>
	 * Currently this value is 2<sup>29</sup>-1.
	 */
	static final int SERIALIZE_MAX_INT = (1 << (5 + Byte.SIZE * 3)) - 1;

	/**
	 * Return the length in bytes of {@code value} when encoded.
	 * <p>
	 * See {@link #encodeInt(int, byte[], int)} for details of the encoding
	 * scheme.
	 *
	 * @param value The value to be encoded.
	 * @return The number of bytes required to encode {@code value}.
	 * @throws IllegalArgumentException if {@value} is outside the range of
	 * 		values that can be encoded.
	 * @see #encodeInt(int, byte[], int)
	 */
	static int intLength(int value) {
		if (value < 0 || value > SERIALIZE_MAX_INT) {
			throw new IllegalArgumentException("Invalid length: " + value);
		}
		// This code is quicker than using a lookup table based on
		// Integer.numerOfLeadingZeros(value) in case you were wondering.
		if (value < 0xA0) { // Numbers less than 160 as is
			return 1;
		} else if (value < (1 << (5 + Byte.SIZE))) { // 13 bit numbers in 2 bytes
			return 2;
		} else if (value < (1 << (5 + Byte.SIZE * 2))) { // 21 bit numbers in 3 bytes
			return 3;
		}
		// Max 29 bit numbers
		return 4;
	}

	/**
	 * Encode the specified {@code value} into a variable number of octets.
	 * <p>
	 * The encoding scheme is given by:
	 * <ol>
	 *   <li>If {@code value} is less than 2<sup>7</sup>, encode it in a
	 *       single byte.</li>
	 *   <li>Otherwise, if {@code value} is less than 2<sup>13</sup>, the
	 *       most significant 5 bits are encoded on the first byte, ored with
	 *       {@code 0x80}, and the remaining 8 bits are encoded on the second
	 *       byte.</li>
	 *   <li>Otherwise, if {@code value} is less than 2<sup>21</sup>, the
	 *       most significant 5 bits are encoded on the first byte, ored with
	 *       {@code 0xA0}, the next most significant 8 bits on the next byte,
	 *       followed by the least significant 8 bits on the final byte.</li>
	 *   <li>Otherwise, if {@code value} is less than 2<sup>29</sup>, the
	 *       most significant 5 bits are encoded on the first byte, ored with
	 *       {@code 0xC0}, the next most significant 8 bits on the next byte,
	 *       and so on for the remaining 3 bytes.</li>
	 * <ol>
	 * Values outside the range [0, 2<sup>29</sup>-1] cannot be
	 * encoded by this method and will throw a {@link IllegalStateException}.
	 *
	 * @param value  The value to be encoded.
	 * @param bytes  The byte buffer to encode the value into.
	 */
	static void encodeInt(int value, ByteBuffer bytes) {
		switch (intLength(value)) {
		case 1:
			bytes.put((byte) value);
			break;
		case 2:
			bytes.put((byte) (((value >>  8) & 0x1F) | 0xA0));
			bytes.put((byte) value);
			break;
		case 3:
			bytes.put((byte) (((value >> 16) & 0x1F) | 0xC0));
			bytes.putShort((short) value);
			break;
		case 4:
			bytes.put((byte) (((value >> 24) & 0x1F) | 0xE0));
			bytes.put((byte) (value >> 16));
			bytes.putShort((short) value);
			break;
		default:
			// Should not be able to get here - programming logic issue
			throw new IllegalArgumentException("Invalid length: " + value);
		}
	}

	/**
	 * Decode a variable-length value from a {@link ByteBuffer}.
	 * <p>
	 * See {@link #encodeInt(int, ByteBuffer)} for details of the encoding
	 * scheme.
	 *
	 * @param bytes The byte buffer from which to decode.
	 * @return The decoded integer.
	 */
	static int decodeInt(ByteBuffer bytes) {
		int b0 = bytes.get() & 0xFF;
		switch (b0 >> 5) {
		case 0:
		case 1:
		case 2:
		case 3:
		case 4:
			return b0;
		case 5:
			int b1 = bytes.get() & 0xFF;
		    return (b0 & 0x1F) << 8 | b1;
		case 6:
			int s1 = bytes.getShort() & 0xFFFF;
		    return (b0 & 0x1F) << 16 | s1;
		case 7:
			int s2 = bytes.getShort() & 0xFFFF;
			int b2 = bytes.get() & 0xFF;
		    return (b0 & 0x1F) << 24 | s2 << 8 | b2;
		default:
			// Should not be able to get here - programming logic issue
			throw new IllegalArgumentException(String.format("Can't decode lead byte of %02X", b0));
		}
	}

	/**
	 * Write an encoded integer to a {@link ByteArrayOutputStream}.
	 *
	 * @param length The integer to write
	 * @param outputStream The output stream to write on
	 * @see #encodeInt(int, ByteBuffer)
	 */
	public static void encodeInt(int length, ByteArrayOutputStream outputStream) {
		byte[] bytes = new byte[0x10]; // Greater than maximum size of encoded number
		ByteBuffer buf = ByteBuffer.wrap(bytes);
		encodeInt(length, buf);
		outputStream.write(bytes, 0, buf.position());
	}
}
