package com.radixdlt.crypto;

/**
 * Exception when an already signed {@link Atom} is signed again
 * TODO this will eventually be removed when Atom, UnsignedAtom and SignedAtom are immutable and separate
 */
public class AtomAlreadySignedException extends CryptoException {
	public AtomAlreadySignedException(String message) {
		super(message);
	}
}
