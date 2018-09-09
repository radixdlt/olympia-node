package com.radixdlt.client.core.crypto;

import java.security.GeneralSecurityException;

import org.bouncycastle.util.encoders.Base64;

public class MacMismatchException extends GeneralSecurityException {
	private final byte[] expected;
	private final byte[] actual;

	public MacMismatchException(String msg, byte[] expected, byte[] actual) {
		super(msg);
		this.expected = expected;
		this.actual = actual;
	}

	public MacMismatchException(byte[] expected, byte[] actual) {
		this.expected = expected;
		this.actual = actual;
	}

	public String getExpectedBase64() {
		return Base64.toBase64String(expected);
	}

	public String getActualBase64() {
		return Base64.toBase64String(actual);
	}
}
