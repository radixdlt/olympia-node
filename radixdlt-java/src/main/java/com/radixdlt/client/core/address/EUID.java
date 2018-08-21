package com.radixdlt.client.core.address;

import java.util.Arrays;

import org.bouncycastle.util.encoders.Hex;

import com.radixdlt.client.core.util.Int128;

public final class EUID {
	public static final int BYTES = Int128.BYTES;

	private final Int128 value;

	public EUID(Int128 value) {
		this.value = value;
	}

	public EUID(byte[] bytes) {
		if (bytes.length > BYTES) {
			bytes = Arrays.copyOf(bytes, BYTES);
		}
		this.value = Int128.from(bytes);
	}

	public EUID(int value) {
		this.value = Int128.from(value);
	}

	public long getShard() {
		return value.getLow();
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof EUID)) {
			return false;
		}

		EUID other = (EUID) o;
		return this.value.equals(other.value);
	}

	@Override
	public String toString() {
		return Hex.toHexString(value.toByteArray());
	}

	/**
	 * Return an array of {@code byte} that represents this {@link EUID}.
	 * Note that the returned array is always {@link #BYTES} bytes in length,
	 * and is padded on the right with the value of the sign bit, if necessary.
	 *
	 * @return An array of {@link #BYTES} bytes.
	 */
	public byte[] toByteArray() {
		byte[] bytes = value.toByteArray();
		if (bytes.length < BYTES) {
			// Pad with sign bit
			byte[] newBytes = new byte[BYTES];
			int fillSize = BYTES - bytes.length;
			byte fill = (bytes[0] < 0) ? (byte) -1 : (byte) 0;
			Arrays.fill(newBytes, 0, fillSize, fill);
			System.arraycopy(bytes, 0, newBytes, fillSize, bytes.length);
			return newBytes;
		}
		return bytes;
	}
}
