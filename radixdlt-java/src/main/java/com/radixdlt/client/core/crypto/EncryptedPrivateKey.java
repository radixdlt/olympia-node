package com.radixdlt.client.core.crypto;

import java.util.Arrays;
import org.bouncycastle.util.encoders.Base64;
import com.radixdlt.client.core.util.Base64Encoded;

public class EncryptedPrivateKey implements Base64Encoded {
	private final byte[] encryptedPrivateKey;

	public EncryptedPrivateKey(byte[] encryptedPrivateKey) {
		this.encryptedPrivateKey = encryptedPrivateKey;
	}

	@Override
	public String base64() {
		return Base64.toBase64String(encryptedPrivateKey);
	}

	@Override
	public byte[] toByteArray() {
		return Arrays.copyOf(encryptedPrivateKey, encryptedPrivateKey.length);
	}
}
