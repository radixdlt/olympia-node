package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.util.Base64Encoded;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.bouncycastle.util.encoders.Base64;

/**
 * Temporary class, will remove in the near future
 */
public class Payload implements Base64Encoded {
	private final byte[] payload;

	// TODO: immutable byte array, a copy?
	public Payload(byte[] payload) {
		this.payload = payload;
	}

	public static Payload fromBase64(String base64Payload) {
		return new Payload(Base64.decode(base64Payload));
	}

	public static Payload fromAscii(String message) {
		return new Payload(message.getBytes());
	}

	public byte[] getBytes() {
		return Arrays.copyOf(payload, payload.length);
	}

	public int length() {
		return payload.length;
	}

	public String base64() {
		return Base64.toBase64String(payload);
	}

	public byte[] toByteArray() {
		return Arrays.copyOf(payload, payload.length);
	}

	public String toUtf8String() {
		return new String(payload, StandardCharsets.UTF_8);
	}
}
