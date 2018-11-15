package com.radixdlt.client.core.crypto;

import java.security.GeneralSecurityException;

public class CryptoException extends GeneralSecurityException {
	public CryptoException() {
		super();
	}

	public CryptoException(String message) {
		super(message);
	}

	public CryptoException(String message, Throwable cause) {
		super(message, cause);
	}
}
