package com.radixdlt.crypto;

/**
 * Exception when an already signed {@link Atom} is signed again
 */
public class AtomAlreadySignedException extends CryptoException {
	public AtomAlreadySignedException(String message) {
		super(message);
	}
}
