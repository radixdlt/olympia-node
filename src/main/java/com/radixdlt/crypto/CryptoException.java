package com.radixdlt.crypto;

@SuppressWarnings("serial")
public class CryptoException extends Exception {
	public CryptoException() {
		super();
	}

	public CryptoException(Throwable arg0) {
		super(arg0);
	}

	public CryptoException(String arg0) {
		super(arg0);
	}

	public CryptoException(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}
}
